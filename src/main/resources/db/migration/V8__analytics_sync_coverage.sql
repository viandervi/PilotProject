-- A successfully queried range is recorded even when YouTube returns no rows.
-- Only completed ranges can be skipped by future incremental syncs.
CREATE TABLE video_analytics_coverage (
    video_id UUID NOT NULL REFERENCES video(id) ON DELETE CASCADE,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (video_id, period_start, period_end),
    CHECK (period_start <= period_end)
);
CREATE INDEX idx_video_analytics_coverage_video ON video_analytics_coverage(video_id, period_end);
