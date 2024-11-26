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

package io.temporal.latencyoptimization.workflowtypes;

import io.temporal.activity.ActivityOptions;
import io.temporal.activity.LocalActivityOptions;
import io.temporal.latencyoptimization.transaction.Transaction;
import io.temporal.latencyoptimization.transaction.TransactionRequest;
import io.temporal.latencyoptimization.transaction.TxResult;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionWorkflowLocalBeforeUpdateImpl implements TransactionWorkflowLocalBeforeUpdate {
  private static final Logger log = LoggerFactory.getLogger(TransactionWorkflowLocalBeforeUpdateImpl.class);

  private final TransactionActivities localActivities =
      Workflow.newLocalActivityStub(
          TransactionActivities.class,
          LocalActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(30)).build());

  private final TransactionActivities activities =
      Workflow.newActivityStub(
          TransactionActivities.class,
          ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(30)).build());

  private boolean initDone = false;
  private Transaction tx;
  private Exception initError = null;

  @Override
  public TxResult processTransaction(TransactionRequest txRequest) {
    this.tx = localActivities.mintTransactionId(txRequest);

    try {
      this.tx = localActivities.initTransaction(this.tx);
    } catch (Exception e) {
      initError = e;
    } finally {
      initDone = true; // Will unblock the early-return returnInitResult method
    }

    return this.tx.finalizeTransaction(activities, txRequest, tx, initError);
  }

  @Override
  public TxResult returnInitResult() {
    Workflow.await(() -> initDone); // Wait for the initialization step of the workflow to complete

    if (initError != null) {
      log.info("Initialization failed.");
      throw Workflow.wrap(initError);
    }

    return new TxResult(
        tx.getId(), "Initialization successful"); // Return the update result to the caller
  }
}
