package io.temporal.latencyoptimization.api;

import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.stream.Collectors;

public class WorkflowResultsStore {
    private final ConcurrentHashMap<String, WorkflowExecutionResult> results = new ConcurrentHashMap<>();

    public void addResult(WorkflowExecutionResult result) {
        results.put(result.getWorkflowId(), result);
    }

    public List<WorkflowExecutionResult> getAllResults() {
        return results.values().stream()
                .sorted((a, b) -> b.getExecutionTimestamp().compareTo(a.getExecutionTimestamp()))
                .collect(Collectors.toList());
    }

    public List<WorkflowExecutionResult> getResultsWithIdPrefix(String prefix) {
        return results.values().stream()
                .filter(result -> result.getWorkflowId().startsWith(prefix))
                .sorted((a, b) -> b.getExecutionTimestamp().compareTo(a.getExecutionTimestamp()))
                .collect(Collectors.toList());
    }
}