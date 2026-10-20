CREATE TABLE creator (
 id UUID PRIMARY KEY,
 google_subject VARCHAR(255) NOT NULL UNIQUE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE youtube_channel (
 id UUID PRIMARY KEY,
 creator_id UUID NOT NULL REFERENCES creator(id),
 youtube_channel_id VARCHAR(128) NOT NULL UNIQUE,
 title TEXT NOT NULL,
 uploads_playlist_id VARCHAR(128),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_youtube_channel_creator ON youtube_channel(creator_id);
CREATE TABLE video (
 id UUID PRIMARY KEY,
 channel_id UUID NOT NULL REFERENCES youtube_channel(id),
 youtube_video_id VARCHAR(32) NOT NULL UNIQUE,
 title TEXT NOT NULL,
 published_at TIMESTAMPTZ,
 duration_seconds INTEGER CHECK (duration_seconds IS NULL OR duration_seconds >= 0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_video_channel ON video(channel_id);
CREATE INDEX idx_video_published ON video(channel_id, published_at DESC);
