package io.temporal.latencyoptimization.api;

import io.javalin.Javalin;
import io.temporal.client.WorkflowClient;
import io.temporal.latencyoptimization.WorkflowRunClient;
import io.temporal.latencyoptimization.transaction.TransactionRequest;
import io.temporal.latencyoptimization.workflowtypes.UpdateWithStartLocalActivitiesImpl;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.latencyoptimization.workflowtypes.UpdateWithStartRegularActivitiesImpl;
import io.temporal.latencyoptimization.workflowtypes.TransactionActivitiesImpl;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

import io.github.cdimascio.dotenv.Dotenv;

public class CallerAPI {
    private static final String TASK_QUEUE = System.getenv().getOrDefault("TEMPORAL_TASK_QUEUE", "LatencyOptimization");
    private final WorkflowClient client;
    private final WorkerFactory factory;
    private final Worker worker;
    private boolean workerRunning = false;
    private final WorkflowResultsStore resultsStore;
    private final ServerInfo serverInfo;

    public CallerAPI(ServerInfo serverInfo) throws FileNotFoundException, SSLException {
        this.serverInfo = serverInfo;

        this.client = TemporalClient.get(serverInfo);
        this.factory = WorkerFactory.newInstance(client);
        this.worker = factory.newWorker(TASK_QUEUE);
        this.resultsStore = new WorkflowResultsStore();

        // Register workflow and activities
        worker.registerWorkflowImplementationTypes(UpdateWithStartRegularActivitiesImpl.class,
                UpdateWithStartLocalActivitiesImpl.class);
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
        System.out.println("Working directory: " + new File(".").getAbsolutePath());
        File envFile = new File("../.env");
        System.out.println(".env file exists: " + envFile.exists());

        Dotenv dotenv = Dotenv.configure()
                .directory("../")
                .load();

        System.out.println("=== All loaded env vars ===");
        dotenv.entries().forEach(entry ->
                System.out.println(entry.getKey() + ": " + entry.getValue())
        );

        String namespace = dotenv.get("TEMPORAL_CONNECTION_NAMESPACE");
        String target = dotenv.get("TEMPORAL_CONNECTION_TARGET");
        String keyFile = dotenv.get("TEMPORAL_CONNECTION_MTLS_KEY_FILE");
        String certChainFile = dotenv.get("TEMPORAL_CONNECTION_MTLS_CERT_CHAIN_FILE");
        String taskQueue = dotenv.get("TEMPORAL_TASK_QUEUE");
        String callerApiPort = dotenv.get("CALLER_API_PORT");
        System.out.println("CALLER_API_PORT: " + callerApiPort);

        ServerInfo serverInfo = new ServerInfo.Builder()
                .namespace(namespace)
                .address(target)
                .certPath(certChainFile)
                .keyPath(keyFile)
                .taskQueue(taskQueue)
                .webPort(callerApiPort).build();

        CallerAPI callerAPI = new CallerAPI(serverInfo);

        // Start the worker
        callerAPI.startWorker();

        Javalin app = Javalin.create();

        app.get("/", ctx -> {
            ctx.json(serverInfo.getServerInfo());
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

                WorkflowExecutionResult result = null;

                String workflowId = request.getId() + "-iteration-" + i;
                String wfType = request.getWf_type();

                WorkflowRunClient earlyReturnClient = new WorkflowRunClient();

                switch (wfType) {
                    case "RegularActivities":
                        result = earlyReturnClient.runWorkflow(
                                callerAPI.client,
                                wfType,
                                workflowId,
                                txRequest,
                                callerAPI.serverInfo
                        );
                        break;
                    case "UpdateWithStartRegularActivities":
                        result = earlyReturnClient.runWorkflowWithUpdateWithStart(
                                callerAPI.client,
                                wfType,
                                workflowId,
                                txRequest,
                                callerAPI.serverInfo
                        );
                        break;
                    case "UpdateWithStartLocalActivities":
                        result = earlyReturnClient.runWorkflowWithUpdateWithStartLocal(
                                callerAPI.client,
                                wfType,
                                workflowId,
                                txRequest,
                                callerAPI.serverInfo
                        );
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid workflow type: " + wfType);
                }

                // Store each result
                callerAPI.resultsStore.addWorkflowRun(request.getId(), request.getIterations(),
                        request.getWf_type(), result);
                results.add(result);
            }

            // Get the complete workflow response
            WorkflowResponse response = callerAPI.resultsStore.getWorkflowResponse(request.getId());
            ctx.json(response);
        });

        int port = Integer.parseInt(serverInfo.getWebPort());
        app.start(port);

        System.out.println("CallerAPI started on port: " + port);
    }
}