CREATE TABLE sync_run (
 id UUID PRIMARY KEY,
 channel_id UUID NOT NULL REFERENCES youtube_channel(id),
 sync_type VARCHAR(40) NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
 attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
 max_attempts INTEGER NOT NULL DEFAULT 5 CHECK (max_attempts > 0),
 next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 lease_until TIMESTAMPTZ,
 started_at TIMESTAMPTZ,
 completed_at TIMESTAMPTZ,
 error_code VARCHAR(100),
 error_message TEXT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT sync_run_status_check CHECK (status IN ('PENDING', 'RUNNING', 'RETRY_WAIT', 'COMPLETED', 'FAILED')),
 CONSTRAINT sync_run_type_check CHECK (sync_type IN ('CHANNEL_SYNC', 'ANALYTICS_SYNC', 'RETENTION_SYNC', 'REACH_SYNC'))
);
CREATE INDEX idx_sync_run_claim ON sync_run(next_attempt_at, created_at)
 WHERE status IN ('PENDING', 'RETRY_WAIT');
CREATE INDEX idx_sync_run_lease ON sync_run(lease_until)
 WHERE status = 'RUNNING';
CREATE INDEX idx_sync_run_channel ON sync_run(channel_id, created_at DESC);
