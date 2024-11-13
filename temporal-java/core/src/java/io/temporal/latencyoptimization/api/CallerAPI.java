package io.temporal.latencyoptimization.api;

import io.javalin.Javalin;
import io.temporal.client.WorkflowClient;
import io.temporal.latencyoptimization.EarlyReturnClient;
import io.temporal.latencyoptimization.TransactionRequest;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.latencyoptimization.TransactionWorkflowImpl;
import io.temporal.latencyoptimization.TransactionActivitiesImpl;

import javax.net.ssl.SSLException;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

public class CallerAPI {
    private static final String TASK_QUEUE = System.getenv().getOrDefault("TEMPORAL_TASK_QUEUE", "LatencyOptimization");
    private final WorkflowClient client;
    private final WorkerFactory factory;
    private final Worker worker;
    private boolean workerRunning = false;
    private final WorkflowResultsStore resultsStore;

    public CallerAPI() throws FileNotFoundException, SSLException {
        this.client = TemporalClient.get();
        this.factory = WorkerFactory.newInstance(client);
        this.worker = factory.newWorker(TASK_QUEUE);
        this.resultsStore = new WorkflowResultsStore();

        // Register workflow and activities
        worker.registerWorkflowImplementationTypes(TransactionWorkflowImpl.class);
        worker.registerActivitiesImplementations(new TransactionActivitiesImpl());
    }

    private void startWorker() {
        if (!workerRunning) {
            factory.start();
            workerRunning = true;
            System.out.println("Worker started on task queue: " + TASK_QUEUE);
        }
    }

    private Map<String, Object> getWorkerStatus() {
        return Map.of(
                "status", workerRunning ? "running" : "stopped",
                "taskQueue", TASK_QUEUE
        );
    }

    public static void main(String[] args) throws FileNotFoundException, SSLException {
        CallerAPI callerAPI = new CallerAPI();

        // Start the worker
        callerAPI.startWorker();

        Javalin app = Javalin.create();

        app.get("/", ctx -> {
            ctx.json(ServerInfo.getServerInfo());
        });

        app.get("/workerstatus", ctx -> {
            ctx.json(callerAPI.getWorkerStatus());
        });

        // Get all workflow results
        app.get("/workflows", ctx -> {
            List<WorkflowResponse> responses = callerAPI.resultsStore.getAllWorkflowResponses();

            String limit = ctx.queryParam("limit");
            if (limit != null) {
                try {
                    int limitNum = Integer.parseInt(limit);
                    responses = callerAPI.resultsStore.getRecentWorkflowResponses(limitNum);
                } catch (NumberFormatException e) {
                    // Invalid limit parameter, just use all responses
                }
            }

            ctx.json(responses);
        });

        // Get specific workflow - note the {id} syntax
        app.get("/workflows/{id}", ctx -> {
            String workflowId = ctx.pathParam("id");
            WorkflowResponse response = callerAPI.resultsStore.getWorkflowResponse(workflowId);

            if (response != null) {
                ctx.json(response);
            } else {
                ctx.status(404).result("Workflow not found");
            }
        });

        app.post("/runWorkflow", ctx -> {
            WorkflowRequest request = ctx.bodyAsClass(WorkflowRequest.class);
            List<WorkflowExecutionResult> results = new ArrayList<>();

            for (int i = 1; i <= request.getIterations(); i++) {
                TransactionRequest txRequest = new TransactionRequest(
                        request.getParams().getSourceAccount(),
                        request.getParams().getTargetAccount(),
                        request.getParams().getAmount()
                );

                String workflowId = request.getId() + "-iteration-" + i;

                EarlyReturnClient earlyReturnClient = new EarlyReturnClient();
                WorkflowExecutionResult result = earlyReturnClient.runWorkflowWithUpdateWithStart(
                        callerAPI.client,
                        workflowId,
                        txRequest
                );

                // Store each result
                callerAPI.resultsStore.addWorkflowRun(request.getId(), request.getIterations(), result);
                results.add(result);
            }

            // Get the complete workflow response
            WorkflowResponse response = callerAPI.resultsStore.getWorkflowResponse(request.getId());
            ctx.json(response);
        });

        int port = Integer.parseInt(System.getenv().getOrDefault("CALLER_API_PORT", "7070"));
        app.start(port);

        System.out.println("CallerAPI started on port: " + port);
    }
}