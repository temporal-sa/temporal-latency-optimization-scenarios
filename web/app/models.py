DEFAULT_WORKFLOW_TYPE = 'TransactionWorkflow'
SCENARIOS = [
    {'id': DEFAULT_WORKFLOW_TYPE, 'label': 'Update-With-Start (regular actvities)'},
    {'id': f"{DEFAULT_WORKFLOW_TYPE}HumanInLoop", 'label': "Require Human-In-Loop Approval"},
    {'id': f"{DEFAULT_WORKFLOW_TYPE}APIDowntime", 'label': "API Downtime (recover on 5th attempt)"},
    {'id': f"{DEFAULT_WORKFLOW_TYPE}RecoverableFailure", 'label': "Bug in Workflow (recoverable failure)",},
    {'id': f"{DEFAULT_WORKFLOW_TYPE}InvalidAccount", 'label': "Invalid Account (unrecoverable failure)",},
]

ACCOUNT_TYPES = [
    {'id': 'checking', 'label': 'Checking'},
    {'id': 'savings', 'label': 'Savings'}
]

ELIGIBLE_RECIPIENTS = [
    {'id': 'justine_morris', 'label': 'Justine Morris'},
    {'id': 'ian_wu', 'label': 'Ian Wu'},
    {'id': 'raul_ruidíaz', 'label': 'Raul Ruidíaz'},
    {'id': 'emma_stockton', 'label': 'Emma Stockton'},
]