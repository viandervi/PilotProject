CREATE TABLE recommendation_action (
    video_id UUID PRIMARY KEY REFERENCES video(id) ON DELETE CASCADE,
    decision VARCHAR(32) NOT NULL CHECK (decision IN ('FIX','REVIVE')),
    score DOUBLE PRECISION NOT NULL CHECK (score >= 0 AND score <= 100),
    confidence DOUBLE PRECISION NOT NULL CHECK (confidence >= 0 AND confidence <= 1),
    reasons TEXT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','APPLIED','DISMISSED')),
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status_updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_recommendation_action_status ON recommendation_action(status, generated_at DESC);
