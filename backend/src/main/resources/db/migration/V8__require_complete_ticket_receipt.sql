-- SQL CHECK treats UNKNOWN as accepted; require a non-null number explicitly.
ALTER TABLE ticket_executions DROP CONSTRAINT ticket_receipt;
ALTER TABLE ticket_executions ADD CONSTRAINT ticket_receipt CHECK (
    (status = 'SUCCEEDED' AND issue_number IS NOT NULL AND issue_number > 0 AND issue_url IS NOT NULL)
    OR (status <> 'SUCCEEDED' AND issue_number IS NULL AND issue_url IS NULL)
);
