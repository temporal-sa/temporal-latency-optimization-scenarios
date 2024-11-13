package io.temporal.latencyoptimization.api;

import java.util.HashMap;
import java.util.Map;

public class ServerInfo {
    private String certPath;
    private String keyPath;
    private String namespace;
    private String address;
    private String taskQueue;
    private String webPort;

    private ServerInfo(Builder builder) {
        this.certPath = builder.certPath;
        this.keyPath = builder.keyPath;
        this.namespace = builder.namespace;
        this.address = builder.address;
        this.taskQueue = builder.taskQueue;
        this.webPort = builder.webPort;
    }

    public String getCertPath() {
        return certPath != null ? certPath : "";
    }

    public String getKeyPath() {
        return keyPath != null ? keyPath : "";
    }

    public String getNamespace() {
        return namespace != null && !namespace.isEmpty() ? namespace : "default";
    }

    public String getAddress() {
        return address != null && !address.isEmpty() ? address : "localhost:7233";
    }

    public String getWebPort() {
        return webPort != null && !webPort.isEmpty() ? webPort : "8080";
    }

    public String getNamespaceUrl() {
        if (getAddress().toLowerCase().contains("localhost")) {
            return String.format("http://localhost:%s/namespaces/%s", getWebPort(), getNamespace());
        }
        return String.format("https://cloud.temporal.io/namespaces/%s", getNamespace());
    }

    public String getWorkflowUrl(String workflowId) {
        if (workflowId == null || workflowId.isEmpty()) {
            throw new IllegalArgumentException("Workflow ID cannot be null or empty");
        }
        return String.format("%s/workflows/%s", getNamespaceUrl(), workflowId);
    }

    public String getTaskQueue() {
        return taskQueue != null && !taskQueue.isEmpty() ? taskQueue : "MoneyTransferJava";
    }

    public Map<String, String> getServerInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("certPath", getCertPath());
        info.put("keyPath", getKeyPath());
        info.put("namespace", getNamespace());
        info.put("address", getAddress());
        info.put("taskQueue", getTaskQueue());
        info.put("webPort", getWebPort());
        return info;
    }

    // Builder for ServerInfo
    public static class Builder {
        private String certPath;
        private String keyPath;
        private String namespace;
        private String address;
        private String taskQueue;
        private String webPort;

        public Builder() {
            // Optionally load default values from environment variables here
            this.certPath = System.getenv("TEMPORAL_CONNECTION_MTLS_KEY_FILE");
            this.keyPath = System.getenv("TEMPORAL_CONNECTION_MTLS_CERT_CHAIN_FILE");
            this.namespace = System.getenv("TEMPORAL_CONNECTION_NAMESPACE");
            this.address = System.getenv("TEMPORAL_CONNECTION_TARGET");
            this.taskQueue = System.getenv("TEMPORAL_TASK_QUEUE");
            this.webPort = System.getenv("TEMPORAL_CONNECTION_WEB_PORT");
        }

        public Builder certPath(String certPath) {
            this.certPath = certPath;
            return this;
        }

        public Builder keyPath(String keyPath) {
            this.keyPath = keyPath;
            return this;
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder address(String address) {
            this.address = address;
            return this;
        }

        public Builder taskQueue(String taskQueue) {
            this.taskQueue = taskQueue;
            return this;
        }

        public Builder webPort(String webPort) {
            this.webPort = webPort;
            return this;
        }

        public ServerInfo build() {
            return new ServerInfo(this);
        }
    }
}
