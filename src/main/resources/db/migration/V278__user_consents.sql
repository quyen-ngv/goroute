-- Consent record (Luật Bảo vệ dữ liệu cá nhân 91/2025/QH15, Nghị định 356/2025/NĐ-CP): every
-- answer a person gives about the Terms, the Privacy Policy, their age and each optional purpose,
-- kept as an append-only history so the operator can show what was agreed, to which document
-- version, when and from which app. The current answer for a purpose is its newest row.
--
-- Kept after an account is deleted: the rows hold no personal data beyond the user id, and they
-- are the proof that past processing had a lawful basis.

CREATE TABLE IF NOT EXISTS user_consents (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID        NOT NULL,
    purpose          VARCHAR(40) NOT NULL,
    granted          BOOLEAN     NOT NULL,
    document_version VARCHAR(20),
    source           VARCHAR(30) NOT NULL,
    platform         VARCHAR(20),
    app_version      VARCHAR(40),
    created_at       TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_user_consents_user_purpose
    ON user_consents (user_id, purpose, created_at DESC);
