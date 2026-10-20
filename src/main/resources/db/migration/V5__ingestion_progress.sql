ALTER TABLE sync_run ADD COLUMN progress_items INTEGER NOT NULL DEFAULT 0;
ALTER TABLE sync_run ADD COLUMN page_cursor TEXT;
CREATE UNIQUE INDEX idx_sync_run_active_channel ON sync_run(channel_id, sync_type)
WHERE status IN ('PENDING','RUNNING','RETRY_WAIT');
