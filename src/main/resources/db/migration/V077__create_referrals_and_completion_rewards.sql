CREATE TABLE IF NOT EXISTS user_referrals (
    id UUID PRIMARY KEY,
    inviter_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invitee_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_referral_invitee UNIQUE (invitee_id),
    CONSTRAINT uk_referral_pair UNIQUE (inviter_id, invitee_id)
);

CREATE INDEX IF NOT EXISTS idx_referrals_code ON user_referrals (code);
