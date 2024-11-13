package io.temporal.latencyoptimization.api;

public class WorkflowRequest {
    private String id;
    private TransactionParams params;
    private String wf_type;
    private String task_queue;
    private int iterations;

    // Nested class for params
    public static class TransactionParams {
        private int amount;
        private String sourceAccount;
        private String targetAccount;

        // Getters and setters
        public int getAmount() { return amount; }
        public void setAmount(int amount) { this.amount = amount; }
        public String getSourceAccount() { return sourceAccount; }
        public void setSourceAccount(String sourceAccount) { this.sourceAccount = sourceAccount; }
        public String getTargetAccount() { return targetAccount; }
        public void setTargetAccount(String targetAccount) { this.targetAccount = targetAccount; }
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public TransactionParams getParams() { return params; }
    public void setParams(TransactionParams params) { this.params = params; }
    public String getWf_type() { return wf_type; }
    public void setWf_type(String wf_type) { this.wf_type = wf_type; }
    public String getTask_queue() { return task_queue; }
    public void setTask_queue(String task_queue) { this.task_queue = task_queue; }
    public int getIterations() { return iterations; }
    public void setIterations(int iterations) { this.iterations = iterations; }
}
