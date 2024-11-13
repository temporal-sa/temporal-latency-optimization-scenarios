import secrets
import string
import signal
from asyncio import sleep
import asyncio
from logging import getLogger
import sys

from dotenv import load_dotenv
from quart import Quart, render_template, g, jsonify, make_response, request, abort, stream_with_context, redirect, json
from quart_cors import cors

# from clients import get_clients
# from temporalio.client import Client
from temporalio.service import RPCError

from app.clients import get_clients
from app.config import get_config
from app.messages import TransferInput
from app.views import get_transfer_money_form, ServerSentEvent
from app.models import DEFAULT_WORKFLOW_TYPE

from aiohttp import ClientSession

logger = getLogger(__name__)

class CustomQuart(Quart):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.app_info = None
        self.sse_connections = dict()  # workflow_id -> set of generators
        self.list_connections = set()  # for list SSE connections
        self.shutting_down = False  # Add shutdown flag

async def shutdown_handler(signal_name=None):
    print('\nShutting down gracefully... (press Ctrl+C again to force)')
    app.shutting_down = True
    
    try:
        # Close workflow-specific SSE connections
        for workflow_id, generators in list(app.sse_connections.items()):
            for generator in list(generators):
                try:
                    generators.discard(generator)
                except Exception:
                    pass
        app.sse_connections.clear()

        # Close list SSE connections
        for generator in list(app.list_connections):
            try:
                app.list_connections.discard(generator)
            except Exception:
                pass
        app.list_connections.clear()

        # Close temporal client
        if hasattr(app, 'clients'):
            app.clients.close()
        
        print('Goodbye!')
        
    except Exception:
        pass
    finally:
        # Get event loop
        try:
            loop = asyncio.get_running_loop()
            # Cancel all running tasks
            tasks = [t for t in asyncio.all_tasks(loop) if t is not asyncio.current_task()]
            for task in tasks:
                task.cancel()
            
            # Allow tasks to cancel
            loop.run_until_complete(asyncio.gather(*tasks, return_exceptions=True))
            loop.stop()
        except Exception:
            pass
        
        # Force exit after a brief delay if we're still running
        sys.exit(0)

load_dotenv()
# so I can attach the app_info object to the app instance
app = CustomQuart(__name__, template_folder='../templates', static_folder='../static')
app = cors(app,
           allow_origin='*',
           allow_headers=['X-Namespace', 'Authorization', 'Accept'],
           # allow_credentials=True,
           allow_methods=['GET', 'PUT', 'POST', 'PATCH','OPTIONS', 'DELETE', 'HEAD'],
           expose_headers=['Content-Type', 'Authorization', 'X-Namespace'])

cfg = get_config()

app_info = dict({
    'name': 'Temporal Latency Optimization',
})
app_info = {**app_info, **cfg}
app.app_info = app_info

@app.before_serving
async def startup():
    clients = await get_clients()
    app.clients = clients
    print('clients are available at `app.clients`')
    
    # Register shutdown handler
    loop = asyncio.get_event_loop()
    
    # Graceful shutdown
    def handle_signal(sig_name):
        """Helper function to properly handle the signal"""
        try:
            loop = asyncio.get_event_loop()
            loop.create_task(shutdown_handler(sig_name))
        except Exception:
            pass

    # Register signal handlers
    for sig in (signal.SIGINT, signal.SIGTERM):
        loop.add_signal_handler(sig, lambda s=sig: handle_signal(s.name))

    # Add custom exception handlers
    def custom_exception_handler(loop, context):
        # Don't show exceptions if we're shutting down
        if not app.shutting_down:
            exception = context.get('exception')
            if not isinstance(exception, (asyncio.CancelledError, RuntimeWarning)):
                loop.default_exception_handler(context)

    loop.set_exception_handler(custom_exception_handler)


@app.after_serving
async def shutdown():
    app.clients.close()


@app.before_request
def apply_app_info():
    g.app_info = app_info


@app.context_processor
def view_app_info():
    return dict(app_info=g.app_info)

# this is a way to expose view helpers for things like url formatting in your templates
@app.context_processor
def url_utils():
    def url_for_namespace():
        conn = cfg.get('temporal',{}).get('connection',{})
        target = conn.get('target','')
        ns = conn.get('namespace', '')
        web_port = conn.get('web_port', '')

        if 'localhost' in target.lower():
            return 'http://localhost:{web_port}/namespaces/{ns}'.format(
                web_port=web_port, ns=ns)
        return 'https://cloud.temporal.io/namespaces/{ns}'.format( ns=ns)

    def url_for_workflow(id):
        return '{ns_url}/workflows/{id}'.format(ns_url=url_for_namespace(),id=id)
    return dict({
        'url_for_namespace': url_for_namespace,
        'url_for_workflow': url_for_workflow
    })

@app.route('/', methods=['GET'])
async def index():
    return redirect(location='/transfers')

@app.route('/debug')
async def debug():
    health = False

    if app.clients.temporal and app.clients.temporal.service_client is not None:
        try:
            health = await app.temporal.service_client.check_health()
        except RPCError as e:
            health = e.message
    return jsonify({
        'app_info': app_info,
        'temporal_client_health': health,
    })


@app.route('/layout')
async def layout():
    return await render_template(template_name_or_list='debug.html')


@app.route('/transfers', methods=['GET'])
async def get_transfers():
    form = await get_transfer_money_form()
    return await render_template(template_name_or_list='index.html', form=form)


# ensures that the workflow is started before the response is sent
async def send_workflow_request(payload, api_port):
    async with ClientSession() as session:
        await session.post(
            f'http://localhost:{api_port}/runWorkflow',
            json=payload
        )

@app.route('/transfers',methods=['POST','PUT'])
async def write_transfers():
    data = await request.form
    wid = data.get('id','transfer-{id}'.format(id=secrets.choice(string.ascii_lowercase + string.digits)))
    wf_type = data.get('scenario',  DEFAULT_WORKFLOW_TYPE)
    params = TransferInput(
        amount=data.get('amount'),
        sourceAccount=data.get('from_account'),
        targetAccount=data.get('to_account'),
        iterations=data.get('iterations'),
    )

    print('sending {params}'.format(params=params))
    conn = cfg.get('temporal',{}).get('worker',{})
    task_queue=conn.get('task_queue','LatencyOptimization')
    api = cfg.get('temporal',{}).get('api',{})
    api_port=api.get('port',7070)
    
    # Create payload for POST request
    payload = {
        "id": wid,
        "params": {
            "amount": params.amount,
            "sourceAccount": params.sourceAccount,
            "targetAccount": params.targetAccount
        },
        "wf_type": wf_type,
        "task_queue": task_queue,
        "iterations": params.iterations
    }
    
    asyncio.create_task(send_workflow_request(payload, api_port))
    return redirect(
        location=f'/transfers/{payload["id"]}?type={wf_type}'
    )

@app.get('/transfers/<id>')
async def transfer(id):
    type = request.args.get('type')
    return await render_template(template_name_or_list='transfer.html', id=id, type=type)


@app.get("/sub/<workflow_id>")
async def sub(workflow_id):
    if "text/event-stream" not in request.accept_mimetypes:
        abort(400)

    api = cfg.get('temporal', {}).get('api', {})
    api_port = api.get('port', 7070)

    @stream_with_context
    async def async_generator():
        async with ClientSession() as session:
            while True:
                # print('querying workflow_id prefix: {workflow_id}'.format(workflow_id=workflow_id))
                
                try:
                    async with session.get(
                        f'http://localhost:{api_port}/workflows/{workflow_id}'
                    ) as response:
                        if response.status == 200:
                            workflow_results = await response.json()
                            event = ServerSentEvent(
                                data=json.dumps(workflow_results),
                                retry=None,
                                id=None,
                                event=None
                            )
                            yield event.encode()
                        else:
                            # error_text = await response.text()
                            # print(f"Error getting workflow data: {error_text}")
                            # Optionally send error event
                            error_event = ServerSentEvent(
                                data=json.dumps({"error": "Failed to fetch workflow data"}),
                                retry=None,
                                id=None,
                                event=None
                            )
                            yield error_event.encode()
                except Exception as e:
                    print(f"Exception while fetching workflow data: {e}")
                    # Optionally send error event
                    error_event = ServerSentEvent(
                        data=json.dumps({"error": str(e)}),
                        retry=None,
                        id=None,
                        event=None
                    )
                    yield error_event.encode()

                await sleep(2)

    response = await make_response(
        async_generator(),
        {
            'Content-Type': 'text/event-stream',
            'Cache-Control': 'no-cache',
            'Transfer-Encoding': 'chunked',
        },
    )
    response.timeout = None
    return response
