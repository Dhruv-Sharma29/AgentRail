ALTER TABLE workflow_runs ADD COLUMN planner_model VARCHAR(64);
ALTER TABLE workflow_runs ADD COLUMN reserved_planner_tokens INTEGER NOT NULL DEFAULT 0 CHECK (reserved_planner_tokens BETWEEN 0 AND 90000);
ALTER TABLE workflow_runs ADD COLUMN planner_tokens INTEGER NOT NULL DEFAULT 0 CHECK (planner_tokens BETWEEN 0 AND 90000);
ALTER TABLE node_executions ADD COLUMN planning_intent_id UUID UNIQUE;
ALTER TABLE node_executions ADD COLUMN planning_request TEXT;
ALTER TABLE node_executions ADD COLUMN planning_deadline TIMESTAMP WITH TIME ZONE;
ALTER TABLE node_executions ADD COLUMN planning_state VARCHAR(20) CHECK (planning_state IN ('REQUESTED', 'RESOLVED', 'ABANDONED'));
ALTER TABLE node_executions ADD CONSTRAINT ck_planning_intent CHECK (
    (planning_intent_id IS NULL AND planning_request IS NULL AND planning_deadline IS NULL AND planning_state IS NULL)
    OR (planning_intent_id IS NOT NULL AND planning_request IS NOT NULL AND planning_deadline IS NOT NULL AND planning_state IS NOT NULL AND agent_settings IS NOT NULL)
);
