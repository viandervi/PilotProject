CREATE TABLE video_daily_metric (
 video_id UUID NOT NULL REFERENCES video(id) ON DELETE CASCADE,
 metric_date DATE NOT NULL,
 views BIGINT NOT NULL CHECK (views >= 0),
 watch_minutes NUMERIC(18,4) NOT NULL CHECK (watch_minutes >= 0),
 average_view_duration NUMERIC(18,4) NOT NULL CHECK (average_view_duration >= 0),
 average_view_percentage NUMERIC(12,4),
 subscribers_gained BIGINT NOT NULL DEFAULT 0 CHECK (subscribers_gained >= 0),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY (video_id, metric_date)
);
CREATE INDEX idx_video_daily_metric_date ON video_daily_metric(metric_date);

CREATE TABLE video_retention_point (
 video_id UUID NOT NULL REFERENCES video(id) ON DELETE CASCADE,
 period_start DATE NOT NULL,
 period_end DATE NOT NULL,
 elapsed_ratio NUMERIC(10,6) NOT NULL CHECK (elapsed_ratio BETWEEN 0 AND 1),
 audience_watch_ratio NUMERIC(14,8) NOT NULL CHECK (audience_watch_ratio >= 0),
 relative_retention_performance NUMERIC(14,8),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY (video_id, period_start, period_end, elapsed_ratio),
 CHECK (period_start <= period_end)
);
