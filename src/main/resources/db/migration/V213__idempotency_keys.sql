-- Generic Idempotency-Key support for authenticated mutating requests (IdempotencyFilter).
--
-- The app's offline replay queue resends a queued POST/PUT/PATCH/DELETE when it never saw
-- the answer (receive timeout, app killed), even though the server may already have
-- committed it. A request carrying an Idempotency-Key header claims one row here, keyed by
-- the caller and the key; a repeat of the same request gets the stored response back instead
-- of running a second time.
--
-- request_fingerprint is SHA-256 (hex) of method, path with query string and SHA-256 of the
-- body, so the same key sent with a different request is told apart. state is IN_PROGRESS
-- while the first request runs and COMPLETED once its response is stored. A request that
-- fails with a 5xx or an exception deletes its row so the client can retry. locked_at lets an
-- IN_PROGRESS row abandoned by a crashed process be taken over after a few minutes.
-- Rows expire after 24 hours and are purged by IdempotencyRecordCleanupJob.
CREATE TABLE IF NOT EXISTS idempotency_keys (
    id                    UUID         PRIMARY KEY,
    user_id               UUID         NOT NULL,
    idempotency_key       VARCHAR(255) NOT NULL,
    request_method        VARCHAR(10)  NOT NULL,
    request_path          VARCHAR(2048) NOT NULL,
    request_fingerprint   CHAR(64)     NOT NULL,
    state                 VARCHAR(16)  NOT NULL,
    response_status       INTEGER,
    response_content_type VARCHAR(255),
    response_body         BYTEA,
    locked_at             TIMESTAMP    NOT NULL,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at            TIMESTAMP    NOT NULL,
    CONSTRAINT chk_idempotency_keys_state CHECK (state IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT chk_idempotency_keys_completed CHECK (state <> 'COMPLETED' OR response_status IS NOT NULL)
);

-- The atomic claim: INSERT ... ON CONFLICT (user_id, idempotency_key) DO NOTHING.
CREATE UNIQUE INDEX IF NOT EXISTS uq_idempotency_keys_user_key
    ON idempotency_keys (user_id, idempotency_key);

-- The nightly purge.
CREATE INDEX IF NOT EXISTS idx_idempotency_keys_expires_at
    ON idempotency_keys (expires_at);
