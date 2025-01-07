package main

import (
	"log"

	"latencyoptimization"
	u "latencyoptimization/utils"

	"github.com/pborman/uuid"
	"go.temporal.io/sdk/client"
	"go.temporal.io/sdk/worker"
)

func main() {
	// The client and worker are heavyweight objects that should be created once per process.
	clientOptions, err := u.LoadClientOptions(false)
	if err != nil {
		log.Fatalf("Failed to load Temporal Cloud environment: %v", err)
	}
	c, err := client.Dial(clientOptions)
	if err != nil {
		log.Fatalln("Unable to create client", err)
	}
	defer c.Close()

	w := worker.New(c, latencyoptimization.TaskQueueName, worker.Options{})

	w.RegisterWorkflow(latencyoptimization.UpdateWithStartLocalActivities)
	w.RegisterWorkflow(latencyoptimization.RegularActivities)
	w.RegisterWorkflow(latencyoptimization.LocalActivities)
	
	transaction := &latencyoptimization.Transaction{ID: uuid.New(), SourceAccount: "Bob", TargetAccount: "Alice", Amount: 100}
	w.RegisterActivity(transaction)

	err = w.Run(worker.InterruptCh())
	if err != nil {
		log.Fatalln("Unable to start worker", err)
	}
}
