CREATE TABLE oauth_credential (
    id UUID PRIMARY KEY,
    creator_id UUID NOT NULL UNIQUE REFERENCES creator(id) ON DELETE CASCADE,
    encrypted_refresh_token TEXT NOT NULL,
    key_version INTEGER NOT NULL DEFAULT 1 CHECK (key_version > 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
