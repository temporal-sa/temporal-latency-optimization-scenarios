from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import List, Optional
# from app.config import get_config

from temporalio.client import Client, WorkflowExecutionStatus
from app.models import DEFAULT_WORKFLOW_TYPE


@dataclass
class WorkflowStatus:
    workflow_id: str
    workflow_status: str
    run_id: str
    start_time: datetime
    close_time: Optional[datetime]
    workflow_type: str
    task_queue: str
    url: str


class TransferLister:
    def __init__(self, client: Client, temporal_config: dict):
        self.client = client
        self.temporal_config = temporal_config

    def _get_namespace_url(self) -> str:
        """Get the base URL for the namespace based on configuration."""
        conn = self.temporal_config.get('temporal',{}).get('connection',{})
        target = conn.get('target', '')
        namespace = conn.get('namespace', '')
        web_port = conn.get('web_port', '')

        if 'localhost' in target.lower():
            return f'http://localhost:{web_port}/namespaces/{namespace}'
        return f'https://cloud.temporal.io/namespaces/{namespace}'

    def _get_workflow_url(self, workflow_id: str) -> str:
        """Generate the URL for a specific workflow."""
        namespace_url = self._get_namespace_url()
        return f'{namespace_url}/workflows/{workflow_id}'

    @staticmethod
    def _get_workflow_status(status: Optional[WorkflowExecutionStatus]) -> str:
        """Convert workflow status to simplified format."""
        if not status:
            return ""
        return status.name.split("_")[-1]

    @staticmethod
    def _get_timestamp_range() -> tuple[str, str]:
        """Get ISO formatted timestamps for now and one hour ago."""
        now = datetime.now(timezone.utc)
        one_hour_ago = now - timedelta(hours=72)
        
        return (
            one_hour_ago.isoformat(),
            now.isoformat()
        )

    async def list_workflows(self) -> List[WorkflowStatus]:
        """List all workflows from the last hour."""
        one_hour_ago, now = self._get_timestamp_range()
        
        # Query for running workflows
        running_workflows = [
            WorkflowStatus(
                workflow_id=wf.id,
                workflow_status=self._get_workflow_status(wf.status),
                run_id=wf.run_id,
                start_time=wf.start_time,
                close_time=wf.close_time,
                workflow_type=wf.workflow_type,
                task_queue=wf.task_queue,
                url=self._get_workflow_url(wf.id)
            )
            async for wf in self.client.list_workflows(
                f"ExecutionStatus = 'Running' "
                f"AND WorkflowType STARTS_WITH '{DEFAULT_WORKFLOW_TYPE}' "
                f"AND StartTime BETWEEN '{one_hour_ago}' AND '{now}'"
            )
        ]
        
        # Query for completed workflows
        completed_workflows = [
            WorkflowStatus(
                workflow_id=wf.id,
                workflow_status=self._get_workflow_status(wf.status),
                run_id=wf.run_id,
                start_time=wf.start_time,
                close_time=wf.close_time,
                workflow_type=wf.workflow_type,
                task_queue=wf.task_queue,
                url=self._get_workflow_url(wf.id)
            )
            async for wf in self.client.list_workflows(
                f"ExecutionStatus != 'Running' "
                f"AND WorkflowType STARTS_WITH '{DEFAULT_WORKFLOW_TYPE}' "
                f"AND StartTime BETWEEN '{one_hour_ago}' AND '{now}'"
            )
        ]

        return running_workflows + completed_workflows
