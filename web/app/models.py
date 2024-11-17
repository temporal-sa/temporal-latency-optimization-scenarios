DEFAULT_WORKFLOW_TYPE = 'RegularActivities'
SCENARIOS = [
    {'id': DEFAULT_WORKFLOW_TYPE, 'label': 'Workflow Start (regular activities)'},
    {'id': 'UpdateWithStartRegularActivities', 'label': 'Update-With-Start (regular activities)'},
    {'id': 'LocalActivities', 'label': 'Workflow Start (local activities)'},
    {'id': "UpdateWithStartLocalActivities", 'label': "Update-With-Start (local activities)"},
    {'id': "EagerLocalActivities", 'label': "Eager Workflow Start (local activities)"},
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