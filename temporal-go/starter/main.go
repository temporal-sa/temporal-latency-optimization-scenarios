package main

import (
	"context"
	"log"
	"time"

	"latencyoptimization"
	
	u "latencyoptimization/utils"

	"github.com/pborman/uuid"
	"go.temporal.io/sdk/client"
	"go.temporal.io/sdk/worker"
)

func main() {
	runLocalActivities()
	runAllRegularActivities()
	runLocalActivitiesBeforeUpdate()
	runEager()
}

func runLocalActivities() {
	clientOptions, err := u.LoadClientOptions(false)
	if err != nil {
		log.Fatalf("Failed to load Temporal Cloud environment: %v", err)
	}
	c, err := client.Dial(clientOptions)
	if err != nil {
		log.Fatalln("Unable to create client", err)
	}
	defer c.Close()

	ctxWithTimeout, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	start := time.Now()

	tx := latencyoptimization.Transaction{ID: uuid.New(), SourceAccount: "Bob", TargetAccount: "Alice", Amount: 100}
	workflowOptions := client.StartWorkflowOptions{
		ID:                 "local-activities-latency-optimization-" + tx.ID,
		TaskQueue:          latencyoptimization.TaskQueueName,
	}
	we, err := c.ExecuteWorkflow(ctxWithTimeout, workflowOptions, latencyoptimization.LocalActivities, tx)
	if err != nil {
		log.Fatalln("Error executing workflow:", err)
	}

	log.Println("Started workflow", "WorkflowID", we.GetID(), "RunID", we.GetRunID())

	// Synchronously wait for the workflow completion.	
	err = we.Get(context.Background(), nil)
	if err != nil {
		log.Fatalln("Unable get workflow result", err)
	}
	timeToFirstResponse := time.Since(start)
	timetoWorkflowEnd := time.Since(start)
	
	log.Println("Time to first response", timeToFirstResponse)
	log.Println("Time to workflow completion", timetoWorkflowEnd)

}

func runLocalActivitiesBeforeUpdate() {
	clientOptions, err := u.LoadClientOptions(false)
	if err != nil {
		log.Fatalf("Failed to load Temporal Cloud environment: %v", err)
	}
	c, err := client.Dial(clientOptions)
	if err != nil {
		log.Fatalln("Unable to create client", err)
	}
	defer c.Close()

	ctxWithTimeout, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	start := time.Now()
	updateOperation := client.NewUpdateWithStartWorkflowOperation(
		client.UpdateWorkflowOptions{
			UpdateName:   latencyoptimization.UpdateName,
			WaitForStage: client.WorkflowUpdateStageCompleted,
		})

	tx := latencyoptimization.Transaction{ID: uuid.New(), SourceAccount: "Bob", TargetAccount: "Alice", Amount: 100}
	workflowOptions := client.StartWorkflowOptions{
		ID:                 "local-before-update-latency-optimization-workflow-ID-" + tx.ID,
		TaskQueue:          latencyoptimization.TaskQueueName,
		WithStartOperation: updateOperation,
	}
	we, err := c.ExecuteWorkflow(ctxWithTimeout, workflowOptions, latencyoptimization.UpdateWithStartLocalActivities, tx)
	if err != nil {
		log.Fatalln("Error executing workflow:", err)
	}

	log.Println("Started workflow", "WorkflowID", we.GetID(), "RunID", we.GetRunID())

	updateHandle, err := updateOperation.Get(ctxWithTimeout)
	if err != nil {
		log.Fatalln("Error obtaining update handle:", err)
	}

	err = updateHandle.Get(ctxWithTimeout, nil)
	if err != nil {
		// The workflow will continue running, cancelling the transaction.

		// NOTE: If the error is retryable, a retry attempt must use a unique workflow ID.
		log.Fatalln("Error obtaining update result:", err)
	}
	timeToFirstResponse := time.Since(start)
	// The workflow will continue running, completing the transaction.

	
	// Synchronously wait for the workflow completion.	
	err = we.Get(context.Background(), nil)
	if err != nil {
		log.Fatalln("Unable get workflow result", err)
	}
	timetoWorkflowEnd := time.Since(start)
	
	log.Println("Time to first response", timeToFirstResponse)
	log.Println("Time to workflow completion", timetoWorkflowEnd)

}

func runAllRegularActivities() {
	clientOptions, err := u.LoadClientOptions(false)
	if err != nil {
		log.Fatalf("Failed to load Temporal Cloud environment: %v", err)
	}
	c, err := client.Dial(clientOptions)
	if err != nil {
		log.Fatalln("Unable to create client", err)
	}
	defer c.Close()

	ctxWithTimeout, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	start := time.Now()
	updateOperation := client.NewUpdateWithStartWorkflowOperation(
		client.UpdateWorkflowOptions{
			UpdateName:   latencyoptimization.UpdateName,
			WaitForStage: client.WorkflowUpdateStageCompleted,
		})

	tx := latencyoptimization.Transaction{ID: uuid.New(), SourceAccount: "Bob", TargetAccount: "Alice", Amount: 100}
	workflowOptions := client.StartWorkflowOptions{
		ID:                 "regular-activities-latency-optimization-workflow-ID-" + tx.ID,
		TaskQueue:          latencyoptimization.TaskQueueName,
		WithStartOperation: updateOperation,
	}
	we, err := c.ExecuteWorkflow(ctxWithTimeout, workflowOptions, latencyoptimization.RegularActivities, tx)
	if err != nil {
		log.Fatalln("Error executing workflow:", err)
	}

	log.Println("Started workflow", "WorkflowID", we.GetID(), "RunID", we.GetRunID())

	updateHandle, err := updateOperation.Get(ctxWithTimeout)
	if err != nil {
		log.Fatalln("Error obtaining update handle:", err)
	}

	err = updateHandle.Get(ctxWithTimeout, nil)
	if err != nil {
		// The workflow will continue running, cancelling the transaction.

		// NOTE: If the error is retryable, a retry attempt must use a unique workflow ID.
		log.Fatalln("Error obtaining update result:", err)
	}
	timeToFirstResponse := time.Since(start)
	// The workflow will continue running, completing the transaction.

	
	// Synchronously wait for the workflow completion.	
	err = we.Get(context.Background(), nil)
	if err != nil {
		log.Fatalln("Unable get workflow result", err)
	}
	timetoWorkflowEnd := time.Since(start)
	
	log.Println("Time to first response", timeToFirstResponse)
	log.Println("Time to workflow completion", timetoWorkflowEnd)

}

func runEager() {
	// 1. Create the shared client.
	clientOptions, err := u.LoadClientOptions(false)
	if err != nil {
		log.Fatalf("Failed to load Temporal Cloud environment: %v", err)
	}
	c, err := client.Dial(clientOptions)
	if err != nil {
		log.Fatalln("Unable to create client", err)
	}
	defer c.Close()

	// 2. Start the worker in a non-blocking manner before the workflow.
	workerOptions := worker.Options{
		OnFatalError: func(err error) { log.Fatalln("Worker error", err) },
	}
	w := worker.New(c, latencyoptimization.TaskQueueName, workerOptions)

	w.RegisterWorkflow(latencyoptimization.UpdateWithStartLocalActivities)
	w.RegisterWorkflow(latencyoptimization.RegularActivities)
	w.RegisterWorkflow(latencyoptimization.LocalActivities)

	err = w.Start()
	if err != nil {
		log.Fatalln("Unable to start worker", err)
	}
	defer w.Stop()

	ctxWithTimeout, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	start := time.Now()

	tx := latencyoptimization.Transaction{ID: uuid.New(), SourceAccount: "Bob", TargetAccount: "Alice", Amount: 100}
	workflowOptions := client.StartWorkflowOptions{
		ID:                 "eager-latency-optimization-workflow-ID-" + tx.ID,
		TaskQueue:          latencyoptimization.TaskQueueName,
		EnableEagerStart: 	true,
	}
	we, err := c.ExecuteWorkflow(ctxWithTimeout, workflowOptions, latencyoptimization.LocalActivities, tx)
	if err != nil {
		log.Fatalln("Error executing workflow:", err)
	}

	log.Println("Started workflow", "WorkflowID", we.GetID(), "RunID", we.GetRunID())

	// The workflow will continue running, completing the transaction.

	
	// Synchronously wait for the workflow completion.	
	err = we.Get(context.Background(), nil)
	if err != nil {
		log.Fatalln("Unable get workflow result", err)
	}
	timeToFirstResponse := time.Since(start)
	timetoWorkflowEnd := time.Since(start)
	
	log.Println("Time to first response", timeToFirstResponse)
	log.Println("Time to workflow completion", timetoWorkflowEnd)

}