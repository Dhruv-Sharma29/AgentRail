-- Retry schedules belong to the committed step checkpoint, never to an in-memory timer.
ALTER TABLE node_executions ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE node_executions ADD CONSTRAINT ck_retry_state CHECK (next_attempt_at IS NULL OR
    (status = 'PENDING' AND attempts BETWEEN 1 AND 2 AND operation IS NOT NULL
        AND agent_settings IS NULL AND approval_settings IS NULL));
