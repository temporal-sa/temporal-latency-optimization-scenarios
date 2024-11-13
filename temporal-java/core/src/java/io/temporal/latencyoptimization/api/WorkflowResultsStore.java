package io.temporal.latencyoptimization.api;

import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class WorkflowResultsStore {
    private final ConcurrentHashMap<String, WorkflowResponse> responses = new ConcurrentHashMap<>();

    public void addWorkflowRun(String workflowId, int iterations, WorkflowExecutionResult result) {
        responses.compute(workflowId, (key, existingResponse) -> {
            if (existingResponse == null) {
                // Create new response with initial result
                List<WorkflowExecutionResult> results = new ArrayList<>();
                results.add(result);
                return new WorkflowResponse(iterations, workflowId, results);
            } else {
                // Add to existing results
                List<WorkflowExecutionResult> updatedResults = new ArrayList<>(existingResponse.getResults());
                updatedResults.add(result);
                return new WorkflowResponse(iterations, workflowId, updatedResults);
            }
        });
    }

    public WorkflowResponse getWorkflowResponse(String workflowId) {
        return responses.get(workflowId);
    }

    public List<WorkflowResponse> getAllWorkflowResponses() {
        return new ArrayList<>(responses.values());
    }

    // Helper method to get the most recent workflow executions
    public List<WorkflowResponse> getRecentWorkflowResponses(int limit) {
        return responses.values().stream()
                .sorted((a, b) -> {
                    // Sort by most recent execution in the results
                    return getLatestTimestamp(b).compareTo(getLatestTimestamp(a));
                })
                .limit(limit)
                .collect(Collectors.toList());
    }

    // Helper to get the latest timestamp from a workflow response
    private static String getLatestTimestamp(WorkflowResponse response) {
        return response.getResults().stream()
                .map(WorkflowExecutionResult::getExecutionTimestamp)
                .max(String::compareTo)
                .orElse("");
    }

    // Clear all results for a specific workflow ID
    public void clearWorkflowResults(String workflowId) {
        responses.remove(workflowId);
    }

    // Clear all results
    public void clearAllResults() {
        responses.clear();
    }
}