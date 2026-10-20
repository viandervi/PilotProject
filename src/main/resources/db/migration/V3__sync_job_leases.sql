ALTER TABLE sync_run ADD COLUMN lease_token UUID;
CREATE INDEX idx_sync_run_reclaim ON sync_run(lease_until) WHERE status = 'RUNNING';
