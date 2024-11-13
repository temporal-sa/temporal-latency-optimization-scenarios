from dataclasses import dataclass

@dataclass
class TransferInput:
    amount: int
    # scenario: str
    # initialSleepTime: int
    sourceAccount: str
    targetAccount: str
    iterations: int

