package io.temporal.latencyoptimization.api;

import java.util.List;

public class WorkflowResponse {
    private final int iterations;
    private final String workflow_type;
    private final String workflow_id;
    private final List<WorkflowExecutionResult> results;

    // Constructor
    public WorkflowResponse(int iterations,
                            String workflow_type,
                            String workflow_id,
                            List<WorkflowExecutionResult> results) {
        this.iterations = iterations;
        this.workflow_type = workflow_type;
        this.workflow_id = workflow_id;
        this.results = results;
    }

    // Getters are required for JSON serialization
    public int getIterations() {
        return iterations;
    }

    public String getScenario() {
        return workflow_type;
    }

    public String getWorkflowId() {
        return workflow_id;
    }

    public List<WorkflowExecutionResult> getResults() {
        return results;
    }
}