/*
 *  Copyright (c) 2020 Temporal Technologies, Inc. All Rights Reserved
 *
 *  Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"). You may not
 *  use this file except in compliance with the License. A copy of the License is
 *  located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package io.temporal.latencyoptimization;

import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.*;
import io.temporal.latencyoptimization.api.ServerInfo;
import io.temporal.latencyoptimization.api.WorkflowExecutionResult;
import io.temporal.latencyoptimization.transaction.TransactionRequest;
import io.temporal.latencyoptimization.transaction.TxResult;
import io.temporal.latencyoptimization.workflowtypes.UpdateWithStartRegularActivities;
import io.temporal.latencyoptimization.workflowtypes.UpdateWithStartLocalActivities;

public class WorkflowRunClient {
    private static final String TASK_QUEUE = System.getenv().getOrDefault("TEMPORAL_TASK_QUEUE", "LatencyOptimization");
    private static final String WORKFLOW_ID_PREFIX = "early-return-workflow-";

//    public static void main(String[] args) throws FileNotFoundException, SSLException {
//        WorkflowClient client = TemporalClient.get();
//        TransactionRequest txRequest =
//                new TransactionRequest(
//                        "Bob", "Alice",
//                        1000);
//        runWorkflowWithUpdateWithStart(client, "early-return", txRequest);
//    }

    public static WorkflowExecutionResult runWorkflow(WorkflowClient client,
                                                                         String wfType,
                                                                         String id,
                                                                         TransactionRequest txRequest,
                                                                         ServerInfo serverInfo) {

        WorkflowOptions options = buildWorkflowOptions(id);
        String workflowId = options.getWorkflowId();

        UpdateWithStartRegularActivities workflow = client.newWorkflowStub(UpdateWithStartRegularActivities.class, options);

        System.out.println("Starting workflow");

        WorkflowExecutionResult.Builder resultBuilder = new WorkflowExecutionResult.Builder()
                .workflowId(workflowId)
                .workflowUrl(serverInfo.getWorkflowUrl(workflowId));

        try {
            // Start timing for overall workflow
            long startTime = System.nanoTime();

            WorkflowExecution workflowHandle =
                    WorkflowClient.start(workflow::processTransaction, txRequest);

            TxResult workflowResult = WorkflowStub.fromTyped(workflow).getResult(TxResult.class);
            System.out.println(
                    "Workflow completed with result: "
                            + workflowResult.getStatus()
                            + " (transactionId: "
                            + workflowResult.getTransactionId()
                            + ")");

            // Calculate workflow latency
            double workflowLatencyMs = (System.nanoTime() - startTime) / 1_000_000.0;

            System.out.println(
                    "Workflow completed with result: "
                            + workflowResult.getStatus()
                            + " (transactionId: "
                            + workflowResult.getTransactionId()
                            + ")");

            return resultBuilder
                    .updateResponseLatencyMs(0)
                    .workflowResponseLatencyMs(workflowLatencyMs)
                    .updateResult(null)
                    .workflowResult(workflowResult)
                    .executionStatus(WorkflowExecutionResult.WorkflowExecutionStatus.COMPLETED)
                    .build();

        } catch (Exception e) {
            if (e.getCause() instanceof io.grpc.StatusRuntimeException) {
                io.grpc.StatusRuntimeException sre = (io.grpc.StatusRuntimeException) e.getCause();
                System.err.println("Workflow failed with StatusRuntimeException: " + sre.getMessage());
                System.err.println("Cause: " + e.getCause());

                if (sre.getStatus().getCode() == io.grpc.Status.Code.PERMISSION_DENIED
                        && sre.getMessage()
                        .contains("ExecuteMultiOperation API is disabled on this namespace")) {
                    System.err.println(
                            "UpdateWithStart requires the ExecuteMultiOperation API to be enabled on this namespace.");
                }
            } else {
                System.err.println("Transaction initialization failed: " + e.getMessage());
                System.err.println("Cause: " + e.getCause());
            }

            return resultBuilder
                    .executionStatus(WorkflowExecutionResult.WorkflowExecutionStatus.FAILED)
                    .build();
        }
    }

    public static WorkflowExecutionResult runWorkflowWithUpdateWithStart(WorkflowClient client,
                                                                         String wfType,
                                                                         String id,
                                                                         TransactionRequest txRequest,
                                                                         ServerInfo serverInfo) {

        WorkflowOptions options = buildWorkflowOptions(id);
        String workflowId = options.getWorkflowId();

        System.out.println("Starting workflow with UpdateWithStart");

        // Create the workflow stub dynamically based on workflowClass
        UpdateWithStartRegularActivities workflow = client.newWorkflowStub(UpdateWithStartRegularActivities.class, options);

        // Prepare the Update-With-Start operation
        UpdateWithStartWorkflowOperation<TxResult> updateOp =
                UpdateWithStartWorkflowOperation.newBuilder(workflow::returnInitResult)
                        .setWaitForStage(WorkflowUpdateStage.COMPLETED) // Wait for update to complete
                        .build();

        TxResult updateResult = null;

        WorkflowExecutionResult.Builder resultBuilder = new WorkflowExecutionResult.Builder()
                .workflowId(workflowId)
                .workflowUrl(serverInfo.getWorkflowUrl(workflowId));

        try {
            // Start timing for overall workflow
            long startTime = System.nanoTime();

            WorkflowUpdateHandle<TxResult> updateHandle =
                    WorkflowClient.updateWithStart(workflow::processTransaction, txRequest, updateOp);

            updateResult = updateHandle.getResultAsync().get();

            System.out.println(
                    "Workflow initialized with result: "
                            + updateResult.getStatus()
                            + " (transactionId: "
                            + updateResult.getTransactionId()
                            + ")");

            // Calculate update latency
            double updateLatencyMs = (System.nanoTime() - startTime) / 1_000_000.0;

            TxResult workflowResult = WorkflowStub.fromTyped(workflow).getResult(TxResult.class);
            System.out.println(
                    "Workflow completed with result: "
                            + workflowResult.getStatus()
                            + " (transactionId: "
                            + workflowResult.getTransactionId()
                            + ")");

            // Calculate workflow latency
            double workflowLatencyMs = (System.nanoTime() - startTime) / 1_000_000.0;

            System.out.println(
                    "Workflow completed with result: "
                            + workflowResult.getStatus()
                            + " (transactionId: "
                            + workflowResult.getTransactionId()
                            + ")");

            return resultBuilder
                    .updateResponseLatencyMs(updateLatencyMs)
                    .workflowResponseLatencyMs(workflowLatencyMs)
                    .updateResult(updateResult)
                    .workflowResult(workflowResult)
                    .executionStatus(WorkflowExecutionResult.WorkflowExecutionStatus.COMPLETED)
                    .build();

        } catch (Exception e) {
            if (e.getCause() instanceof io.grpc.StatusRuntimeException) {
                io.grpc.StatusRuntimeException sre = (io.grpc.StatusRuntimeException) e.getCause();
                System.err.println("Workflow failed with StatusRuntimeException: " + sre.getMessage());
                System.err.println("Cause: " + e.getCause());

                if (sre.getStatus().getCode() == io.grpc.Status.Code.PERMISSION_DENIED
                        && sre.getMessage()
                        .contains("ExecuteMultiOperation API is disabled on this namespace")) {
                    System.err.println(
                            "UpdateWithStart requires the ExecuteMultiOperation API to be enabled on this namespace.");
                }
            } else {
                System.err.println("Transaction initialization failed: " + e.getMessage());
                System.err.println("Cause: " + e.getCause());
            }

            return resultBuilder
                    .executionStatus(WorkflowExecutionResult.WorkflowExecutionStatus.FAILED)
                    .build();
        }
    }

    // TODO: Fix lots of duplicate code shared with the above method
    public static WorkflowExecutionResult runWorkflowWithUpdateWithStartLocal(WorkflowClient client,
                                                                         String wfType,
                                                                         String id,
                                                                         TransactionRequest txRequest,
                                                                         ServerInfo serverInfo) {

        WorkflowOptions options = buildWorkflowOptions(id);
        String workflowId = options.getWorkflowId();

        System.out.println("Starting workflow with UpdateWithStart");

        // Create the workflow stub dynamically based on workflowClass
        UpdateWithStartLocalActivities workflow = client.newWorkflowStub(UpdateWithStartLocalActivities.class, options);

        // Prepare the Update-With-Start operation
        UpdateWithStartWorkflowOperation<TxResult> updateOp =
                UpdateWithStartWorkflowOperation.newBuilder(workflow::returnInitResult)
                        .setWaitForStage(WorkflowUpdateStage.COMPLETED) // Wait for update to complete
                        .build();

        TxResult updateResult = null;

        WorkflowExecutionResult.Builder resultBuilder = new WorkflowExecutionResult.Builder()
                .workflowId(workflowId)
                .workflowUrl(serverInfo.getWorkflowUrl(workflowId));

        try {
            // Start timing for overall workflow
            long startTime = System.nanoTime();

            WorkflowUpdateHandle<TxResult> updateHandle =
                    WorkflowClient.updateWithStart(workflow::processTransaction, txRequest, updateOp);

            updateResult = updateHandle.getResultAsync().get();

            System.out.println(
                    "Workflow initialized with result: "
                            + updateResult.getStatus()
                            + " (transactionId: "
                            + updateResult.getTransactionId()
                            + ")");

            // Calculate update latency
            double updateLatencyMs = (System.nanoTime() - startTime) / 1_000_000.0;

            TxResult workflowResult = WorkflowStub.fromTyped(workflow).getResult(TxResult.class);
            System.out.println(
                    "Workflow completed with result: "
                            + workflowResult.getStatus()
                            + " (transactionId: "
                            + workflowResult.getTransactionId()
                            + ")");

            // Calculate workflow latency
            double workflowLatencyMs = (System.nanoTime() - startTime) / 1_000_000.0;

            System.out.println(
                    "Workflow completed with result: "
                            + workflowResult.getStatus()
                            + " (transactionId: "
                            + workflowResult.getTransactionId()
                            + ")");

            return resultBuilder
                    .updateResponseLatencyMs(updateLatencyMs)
                    .workflowResponseLatencyMs(workflowLatencyMs)
                    .updateResult(updateResult)
                    .workflowResult(workflowResult)
                    .executionStatus(WorkflowExecutionResult.WorkflowExecutionStatus.COMPLETED)
                    .build();

        } catch (Exception e) {
            if (e.getCause() instanceof io.grpc.StatusRuntimeException) {
                io.grpc.StatusRuntimeException sre = (io.grpc.StatusRuntimeException) e.getCause();
                System.err.println("Workflow failed with StatusRuntimeException: " + sre.getMessage());
                System.err.println("Cause: " + e.getCause());

                if (sre.getStatus().getCode() == io.grpc.Status.Code.PERMISSION_DENIED
                        && sre.getMessage()
                        .contains("ExecuteMultiOperation API is disabled on this namespace")) {
                    System.err.println(
                            "UpdateWithStart requires the ExecuteMultiOperation API to be enabled on this namespace.");
                }
            } else {
                System.err.println("Transaction initialization failed: " + e.getMessage());
                System.err.println("Cause: " + e.getCause());
            }

            return resultBuilder
                    .executionStatus(WorkflowExecutionResult.WorkflowExecutionStatus.FAILED)
                    .build();
        }
    }

    // Build WorkflowOptions with task queue and unique ID
    private static WorkflowOptions buildWorkflowOptions(String id) {
        return WorkflowOptions.newBuilder()
                .setTaskQueue(TASK_QUEUE)
                .setWorkflowId(id)
                .build();
    }
}
