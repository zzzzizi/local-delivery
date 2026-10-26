ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE account_tokens (
    token_hash CHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purpose VARCHAR(20) NOT NULL CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (user_id, purpose)
);
CREATE INDEX ix_account_tokens_expiry ON account_tokens(expires_at);
