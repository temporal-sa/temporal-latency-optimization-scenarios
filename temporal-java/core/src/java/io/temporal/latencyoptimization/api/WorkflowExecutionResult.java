package io.temporal.latencyoptimization.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.temporal.latencyoptimization.TxResult;

public class WorkflowExecutionResult {
    private final String workflowId;
    private final double updateResponseLatencyMs;
    private final double workflowResponseLatencyMs;
    private final TxResult updateResult;
    private final TxResult workflowResult;
    private final String executionTimestamp;
    private final WorkflowExecutionStatus executionStatus;
    private final String workflowUrl;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public WorkflowExecutionResult(
            @JsonProperty("workflowId") String workflowId,
            @JsonProperty("updateResponseLatencyMs") double updateResponseLatencyMs,
            @JsonProperty("workflowResponseLatencyMs") double workflowResponseLatencyMs,
            @JsonProperty("updateResult") TxResult updateResult,
            @JsonProperty("workflowResult") TxResult workflowResult,
            @JsonProperty("executionTimestamp") String executionTimestamp,
            @JsonProperty("executionStatus") WorkflowExecutionStatus executionStatus,
            @JsonProperty("workflowUrl") String workflowUrl){
        this.workflowId = workflowId;
        this.updateResponseLatencyMs = updateResponseLatencyMs;
        this.workflowResponseLatencyMs = workflowResponseLatencyMs;
        this.updateResult = updateResult;
        this.workflowResult = workflowResult;
        this.executionTimestamp = executionTimestamp;
        this.executionStatus = executionStatus;
        this.workflowUrl = workflowUrl;
    }

    @JsonProperty("workflowId")
    public String getWorkflowId() {
        return workflowId;
    }

    @JsonProperty("updateResponseLatencyMs")
    public double getUpdateResponseLatencyMs() {
        return updateResponseLatencyMs;
    }

    @JsonProperty("workflowResponseLatencyMs")
    public double getWorkflowResponseLatencyMs() {
        return workflowResponseLatencyMs;
    }

    @JsonProperty("updateResult")
    public TxResult getUpdateResult() {
        return updateResult;
    }

    @JsonProperty("workflowResult")
    public TxResult getWorkflowResult() {
        return workflowResult;
    }

    @JsonProperty("executionTimestamp")
    public String getExecutionTimestamp() {
        return executionTimestamp;
    }

    @JsonProperty("executionStatus")
    public WorkflowExecutionStatus getExecutionStatus() {
        return executionStatus;
    }

    @JsonProperty("workflowUrl")
    public String getWorkflowUrl() {
        return workflowUrl;
    }

    // Builder pattern for easier object creation
    public static class Builder {
        private String workflowId;
        private double updateResponseLatencyMs;
        private double workflowResponseLatencyMs;
        private TxResult updateResult;
        private TxResult workflowResult;
        private String executionTimestamp;
        private WorkflowExecutionStatus executionStatus;
        private String workflowUrl;

        public Builder() {
            this.executionTimestamp = java.time.Instant.now().toString();
            this.executionStatus = WorkflowExecutionStatus.COMPLETED;
        }

        public Builder workflowId(String workflowId) {
            this.workflowId = workflowId;
            return this;
        }

        public Builder updateResponseLatencyMs(double latency) {
            this.updateResponseLatencyMs = latency;
            return this;
        }

        public Builder workflowResponseLatencyMs(double latency) {
            this.workflowResponseLatencyMs = latency;
            return this;
        }

        public Builder updateResult(TxResult result) {
            this.updateResult = result;
            return this;
        }

        public Builder workflowResult(TxResult result) {
            this.workflowResult = result;
            return this;
        }

        public Builder executionStatus(WorkflowExecutionStatus status) {
            this.executionStatus = status;
            return this;
        }

        public Builder workflowUrl(String url) {
            this.workflowUrl = url;
            return this;
        }

        public WorkflowExecutionResult build() {
            return new WorkflowExecutionResult(
                    workflowId,
                    updateResponseLatencyMs,
                    workflowResponseLatencyMs,
                    updateResult,
                    workflowResult,
                    executionTimestamp,
                    executionStatus,
                    workflowUrl
            );
        }
    }

    // Enum for execution status
    public enum WorkflowExecutionStatus {
        COMPLETED,
        FAILED,
    }

    @Override
    public String toString() {
        return String.format(
                "WorkflowExecutionResult{workflowId='%s', updateLatency=%.2fms, workflowLatency=%.2fms, status=%s, url='%s'}",
                workflowId,
                updateResponseLatencyMs,
                workflowResponseLatencyMs,
                executionStatus,
                workflowUrl
        );
    }
}