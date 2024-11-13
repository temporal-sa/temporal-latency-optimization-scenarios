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

import io.temporal.client.WorkflowClient;
import io.temporal.latencyoptimization.api.TemporalClient;
import io.temporal.latencyoptimization.workflowtypes.UpdateWithStartRegularActivitiesImpl;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

import javax.net.ssl.SSLException;
import java.io.FileNotFoundException;

public class EarlyReturnWorker {
  private static final String TASK_QUEUE = System.getenv().getOrDefault("TEMPORAL_TASK_QUEUE", "LatencyOptimization");

  public static void main(String[] args) throws FileNotFoundException, SSLException {
    WorkflowClient client = TemporalClient.get();
    startWorker(client);
  }

  private static void startWorker(WorkflowClient client) {
    WorkerFactory factory = WorkerFactory.newInstance(client);
    Worker worker = factory.newWorker(TASK_QUEUE);

    worker.registerWorkflowImplementationTypes(UpdateWithStartRegularActivitiesImpl.class);
    worker.registerActivitiesImplementations(new TransactionActivitiesImpl());

    factory.start();
    System.out.println("Worker started");
  }
}
