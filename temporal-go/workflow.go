package latencyoptimization

import (
	"context"
	"errors"
	"fmt"
	"time"

	"go.temporal.io/sdk/activity"
	"go.temporal.io/sdk/workflow"
)

const (
	UpdateName    = "early-return"
	TaskQueueName = "latency-optimization-tq"
)

type Transaction struct {
	ID            string
	SourceAccount string
	TargetAccount string
	Amount        int // in cents

	initErr  error
	initDone bool
}

// UpdateWithStartLocalActivities processes a transaction in two phases. First, the transaction is initialized, and if successful,
// it proceeds to completion. However, if initialization fails - due to validation errors or transient
// issues (e.g., network connectivity problems) - the transaction is cancelled.
//
// By utilizing Update-with-Start, the client can initiate the workflow and immediately receive the result of
// the initialization in a single round trip, even before the transaction processing completes. The remainder
// of the transaction is then processed asynchronously.
func UpdateWithStartLocalActivities(ctx workflow.Context, tx Transaction) error {
	return run(ctx, tx)
}

func run(ctx workflow.Context, tx Transaction) error {
	logger := workflow.GetLogger(ctx)

	if err := workflow.SetUpdateHandler(
		ctx,
		UpdateName,
		tx.returnInitResult,
	); err != nil {
		return err
	}

	// Phase 1: Initialize the transaction synchronously.
	//
	// By using a local activity, an additional server roundtrip is avoided.
	// See https://docs.temporal.io/activities#local-activity for more details.

	activityOptions := workflow.WithLocalActivityOptions(ctx, workflow.LocalActivityOptions{
		ScheduleToCloseTimeout: 5 * time.Second, // short timeout to avoid another Workflow Task being scheduled
	})
	workflow.ExecuteLocalActivity(activityOptions, tx.RegisterTransactionID).Get(ctx, nil)
	tx.initErr = workflow.ExecuteLocalActivity(activityOptions, tx.InitTransaction).Get(ctx, nil)
	tx.initDone = true

	// Phase 2: Complete or cancel the transaction asychronously.

	activityCtx := workflow.WithActivityOptions(ctx, workflow.ActivityOptions{
		StartToCloseTimeout: 30 * time.Second,
	})
	if tx.initErr != nil {
		logger.Error(fmt.Sprintf("cancelling transaction due to init error: %v", tx.initErr))

		// Transaction failed to be initialized or not quickly enough; cancel the transaction.
		if err := workflow.ExecuteActivity(activityCtx, tx.CancelTransaction).Get(ctx, nil); err != nil {
			return fmt.Errorf("cancelling the transaction failed: %w", err)
		}

		return tx.initErr
	}

	logger.Info("completing transaction")

	// Transaction was initialized successfully; complete the transaction.
	if err := workflow.ExecuteActivity(activityCtx, tx.CompleteTransaction).Get(ctx, nil); err != nil {
		return fmt.Errorf("completing the transaction failed: %w", err)
	}

	return nil
}

func RegularActivities(ctx workflow.Context, tx Transaction) error {
	return runAllRegularActivities(ctx, tx)
}

func runAllRegularActivities(ctx workflow.Context, tx Transaction) error {
	logger := workflow.GetLogger(ctx)

	if err := workflow.SetUpdateHandler(
		ctx,
		UpdateName,
		tx.returnInitResult,
	); err != nil {
		return err
	}

	// Phase 1: Initialize the transaction synchronously.

	activityOptions := workflow.WithActivityOptions(ctx, workflow.ActivityOptions{
		ScheduleToCloseTimeout: 5 * time.Second, // short timeout to avoid another Workflow Task being scheduled
	})
	workflow.ExecuteActivity(activityOptions, tx.RegisterTransactionID).Get(ctx, nil)
	tx.initErr = workflow.ExecuteActivity(activityOptions, tx.InitTransaction).Get(ctx, nil)
	tx.initDone = true

	// Phase 2: Complete or cancel the transaction asychronously.

	activityCtx := workflow.WithActivityOptions(ctx, workflow.ActivityOptions{
		StartToCloseTimeout: 30 * time.Second,
	})
	if tx.initErr != nil {
		logger.Error(fmt.Sprintf("cancelling transaction due to init error: %v", tx.initErr))

		// Transaction failed to be initialized or not quickly enough; cancel the transaction.
		if err := workflow.ExecuteActivity(activityCtx, tx.CancelTransaction).Get(ctx, nil); err != nil {
			return fmt.Errorf("cancelling the transaction failed: %w", err)
		}

		return tx.initErr
	}

	logger.Info("completing transaction")

	// Transaction was initialized successfully; complete the transaction.
	if err := workflow.ExecuteActivity(activityCtx, tx.CompleteTransaction).Get(ctx, nil); err != nil {
		return fmt.Errorf("completing the transaction failed: %w", err)
	}

	return nil
}

// local activities only version
func LocalActivities(ctx workflow.Context, tx Transaction) error {
	return runLocal(ctx, tx)
}

func runLocal(ctx workflow.Context, tx Transaction) error {
	logger := workflow.GetLogger(ctx)

	if err := workflow.SetUpdateHandler(
		ctx,
		UpdateName,
		tx.returnInitResult,
	); err != nil {
		return err
	}

	// Phase 1: Initialize the transaction synchronously.
	//
	// By using a local activity, an additional server roundtrip is avoided.
	// See https://docs.temporal.io/activities#local-activity for more details.

	activityOptions := workflow.WithLocalActivityOptions(ctx, workflow.LocalActivityOptions{
		ScheduleToCloseTimeout: 5 * time.Second, // short timeout to avoid another Workflow Task being scheduled
	})
	workflow.ExecuteLocalActivity(activityOptions, tx.RegisterTransactionID).Get(ctx, nil)
	tx.initErr = workflow.ExecuteLocalActivity(activityOptions, tx.InitTransaction).Get(ctx, nil)
	tx.initDone = true

	// Phase 2: Complete or cancel the transaction asychronously.

	activityCtx := workflow.WithLocalActivityOptions(ctx, workflow.LocalActivityOptions{
		StartToCloseTimeout: 30 * time.Second,
	})
	if tx.initErr != nil {
		logger.Error(fmt.Sprintf("cancelling transaction due to init error: %v", tx.initErr))

		// Transaction failed to be initialized or not quickly enough; cancel the transaction.
		if err := workflow.ExecuteLocalActivity(activityCtx, tx.CancelTransaction).Get(ctx, nil); err != nil {
			return fmt.Errorf("cancelling the transaction failed: %w", err)
		}

		return tx.initErr
	}

	logger.Info("completing transaction")

	// Transaction was initialized successfully; complete the transaction.
	if err := workflow.ExecuteLocalActivity(activityCtx, tx.CompleteTransaction).Get(ctx, nil); err != nil {
		return fmt.Errorf("completing the transaction failed: %w", err)
	}

	return nil
}

func (tx *Transaction) returnInitResult(ctx workflow.Context) error {
	if err := workflow.Await(ctx, func() bool { return tx.initDone }); err != nil {
		return fmt.Errorf("transaction init cancelled: %w", err)
	}
	return tx.initErr
}

func (tx *Transaction) RegisterTransactionID(ctx context.Context) error {
	logger := activity.GetLogger(ctx)

	time.Sleep(10 * time.Millisecond)
	logger.Info("Transaction ID registered")
	return nil
}

func (tx *Transaction) InitTransaction(ctx context.Context) error {
	logger := activity.GetLogger(ctx)
	if tx.Amount <= 0 {
		return errors.New("invalid Amount")
	}
	time.Sleep(10 * time.Millisecond)
	logger.Info("Transaction initialized")
	return nil
}

func (tx *Transaction) CancelTransaction(ctx context.Context) error {
	logger := activity.GetLogger(ctx)
	time.Sleep(10 * time.Millisecond)
	logger.Info("Transaction cancelled")
	return nil
}

func (tx *Transaction) CompleteTransaction(ctx context.Context) error {
	logger := activity.GetLogger(ctx)
	time.Sleep(100 * time.Millisecond)
	logger.Info("Transaction completed")
	return nil
}
