import secrets
import string
import signal
from asyncio import sleep
import asyncio
from typing import cast
from logging import getLogger
import sys

from dotenv import load_dotenv
from quart import Quart, render_template, g, jsonify, make_response, request, abort, stream_with_context, redirect, json
from quart_cors import cors

# from clients import get_clients
from temporalio.client import Client
from temporalio.service import RPCError

from app.clients import get_clients
from app.config import get_config
from app.messages import TransferInput
from app.models import DEFAULT_WORKFLOW_TYPE
from app.views import get_transfer_money_form, ServerSentEvent
from app.list_workflows import TransferLister

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
    'name': 'Temporal Money Transfer'
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
    
    # Register signal handlers - corrected version
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

        if 'localhost' in target.lower():
            return 'http://localhost:8233/namespaces/{ns}'.format( ns=ns)
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


@app.route('/transfers',methods=['POST','PUT'])
async def write_transfers():
    temporal_client = cast(Client, app.clients.temporal)
    data = await request.form
    wid = data.get('id','transfer-{id}'.format(id=secrets.choice(string.ascii_lowercase + string.digits)))
    wf_type = data.get('scenario',  DEFAULT_WORKFLOW_TYPE)
    params = TransferInput(
        amount=data.get('amount'),
        fromAccount=data.get('from_account'),
        toAccount=data.get('to_account'),
    )
    print('sending {params}'.format(params=params))

    handle = await temporal_client.start_workflow(wf_type,
                                                  id=wid,
                                                  task_queue='MoneyTransfer',
                                                  arg=params,
                                                  )

    return redirect(location='/transfers/{id}?type={wf_type}'.format(id=handle.id, wf_type=wf_type))

@app.route('/approvals/<workflow_id>', methods=['PUT','POST'])
async def approve(workflow_id):
    temporal_client = cast(Client, app.clients.temporal)
    handle = temporal_client.get_workflow_handle(workflow_id)
    await handle.signal(signal='approveTransfer')
    return redirect(location=f'/transfers/{workflow_id}')

@app.get('/transfers/<id>')
async def transfer(id):
    type = request.args.get('type')
    return await render_template(template_name_or_list='transfer.html', id=id, type=type)

@app.get("/sub/<workflow_id>")
async def sub(workflow_id):
    if "text/event-stream" not in request.accept_mimetypes:
        abort(400)
    
    @stream_with_context
    async def async_generator():
        if workflow_id not in app.sse_connections:
            app.sse_connections[workflow_id] = set()
        
        app.sse_connections[workflow_id].add(async_generator)
        logger.info(f"New SSE connection for workflow {workflow_id}")
        
        try:
            while not app.shutting_down:  # Check shutdown flag
                try:
                    print('querying {workflow_id}'.format(workflow_id=workflow_id))
                    handle = app.clients.temporal.get_workflow_handle(workflow_id)
                    state = await handle.query('transferStatus')
                    print(state)
                    event = ServerSentEvent(data=json.dumps(state), retry=None, id=None, event=None)
                    yield event.encode()
                    await sleep(2)
                except asyncio.CancelledError:
                    logger.info(f"SSE connection cancelled for workflow {workflow_id}")
                    break
                except Exception as e:
                    logger.error(f"Error in workflow SSE: {str(e)}")
                    break
        finally:
            if workflow_id in app.sse_connections:
                app.sse_connections[workflow_id].discard(async_generator)
                if not app.sse_connections[workflow_id]:
                    del app.sse_connections[workflow_id]
            logger.info(f"Closed SSE connection for workflow {workflow_id}")

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


@app.get("/sub/list")
async def sub_list():
    if "text/event-stream" not in request.accept_mimetypes:
        abort(400)
    
    @stream_with_context
    async def async_generator():
        app.list_connections.add(async_generator)
        logger.info("New SSE connection for workflow list")
        
        try:
            while not app.shutting_down:  # Check shutdown flag
                try:
                    lister = TransferLister(client=app.clients.temporal, temporal_config=cfg)
                    workflows = await lister.list_workflows()
                    
                    for workflow in workflows:
                        print(
                            f"ID: {workflow.workflow_id}\n"
                            f"Status: {workflow.workflow_status}\n"
                            f"Run ID: {workflow.run_id}\n"
                            f"Type: {workflow.workflow_type}\n"
                            f"Started: {workflow.start_time}\n"
                            f"Closed: {workflow.close_time or 'Still running'}\n"
                            f"Task Queue: {workflow.task_queue}\n"
                        )
                    
                    state = workflows
                    event = ServerSentEvent(data=json.dumps(state), retry=None, id=None, event=None)
                    yield event.encode()
                    await sleep(2)
                except asyncio.CancelledError:
                    logger.info("SSE list connection cancelled")
                    break
                except Exception as e:
                    logger.error(f"Error in list SSE: {str(e)}")
                    break
        finally:
            app.list_connections.discard(async_generator)
            logger.info("Closed SSE connection for workflow list")

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