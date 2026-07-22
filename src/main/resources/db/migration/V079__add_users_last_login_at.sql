ALTER TABLE users
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_users_last_login_at
    ON users(last_login_at DESC)
    WHERE deleted_at IS NULL;
