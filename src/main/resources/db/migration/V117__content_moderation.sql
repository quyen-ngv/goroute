-- Epic 12 (MOD-02, MOD-04, MOD-06, MOD-08) and Epic 05 (SOC-06a).
-- One shared filter, one shared flag stream, one shared review queue.

-- MOD-02: administrable term list. Exemptions live in the same table so that the
-- matcher loads a single snapshot and always evaluates exemptions first.
CREATE TABLE moderation_terms (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    term            VARCHAR(200) NOT NULL,
    normalized_term VARCHAR(200) NOT NULL,
    category        VARCHAR(40)  NOT NULL,
    action          VARCHAR(20)  NOT NULL,
    language        VARCHAR(10)  NOT NULL DEFAULT 'vi',
    is_exemption    BOOLEAN      NOT NULL DEFAULT FALSE,
    note            TEXT,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    data_version    BIGINT       NOT NULL DEFAULT 1,
    created_by      UUID,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_moderation_term UNIQUE (normalized_term, language, is_exemption),
    CONSTRAINT ck_moderation_term_action CHECK (action IN ('BLOCK', 'FLAG', 'LOG', 'ALLOW'))
);
CREATE INDEX idx_moderation_terms_active ON moderation_terms (is_active, is_exemption);

-- Every change to the list is audited: this table decides whether user content is
-- publishable, so "who changed what" is not optional.
CREATE TABLE moderation_term_audit (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    term_id      UUID NOT NULL,
    action       VARCHAR(20) NOT NULL,
    before_value JSONB,
    after_value  JSONB,
    changed_by   UUID,
    changed_at   TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_moderation_term_audit_term ON moderation_term_audit (term_id, changed_at DESC);

-- MOD-08: one row per filter decision. False-positive and miss rates are computed
-- from here, so it is written from the day the filter is switched on.
CREATE TABLE moderation_decisions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_type   VARCHAR(40) NOT NULL,
    content_id     UUID,
    field_label    VARCHAR(80),
    user_id        UUID,
    visibility     VARCHAR(20) NOT NULL,
    layer          VARCHAR(20) NOT NULL,
    decision       VARCHAR(20) NOT NULL,
    category       VARCHAR(40),
    matched_term_id UUID REFERENCES moderation_terms (id) ON DELETE SET NULL,
    matched_text   VARCHAR(200),
    policy_version VARCHAR(20) NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_moderation_decisions_created ON moderation_decisions (created_at DESC);
CREATE INDEX idx_moderation_decisions_term ON moderation_decisions (matched_term_id, decision);
CREATE INDEX idx_moderation_decisions_content ON moderation_decisions (content_type, content_id);

-- MOD-06: the shared human queue. Deliberately NOT tied to any one content table --
-- review_flags stays where it is and keeps serving fraud scoring.
CREATE TABLE moderation_flags (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_type     VARCHAR(40) NOT NULL,
    content_id       UUID NOT NULL,
    content_owner_id UUID,
    source           VARCHAR(30) NOT NULL,
    category         VARCHAR(40) NOT NULL,
    severity         VARCHAR(20) NOT NULL,
    priority         INT NOT NULL DEFAULT 0,
    reason           TEXT,
    context_snapshot JSONB,
    report_count     INT NOT NULL DEFAULT 0,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    resolution_note  TEXT,
    reviewed_by      UUID,
    reviewed_at      TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_moderation_flag_status CHECK (status IN ('PENDING', 'KEPT', 'REMOVED', 'ESCALATED')),
    -- One open flag per (content, source, category): repeated reports raise
    -- report_count and priority instead of flooding the queue.
    CONSTRAINT uq_moderation_flag_open UNIQUE (content_type, content_id, source, category)
);
CREATE INDEX idx_moderation_flags_queue ON moderation_flags (status, priority DESC, created_at);
CREATE INDEX idx_moderation_flags_owner ON moderation_flags (content_owner_id);

-- SOC-06a: user reports. A user may report one piece of content once.
CREATE TABLE content_reports (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_type VARCHAR(40) NOT NULL,
    content_id   UUID NOT NULL,
    reporter_id  UUID NOT NULL,
    reason       VARCHAR(40) NOT NULL,
    note         TEXT,
    flag_id      UUID REFERENCES moderation_flags (id) ON DELETE SET NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_content_report_once UNIQUE (content_type, content_id, reporter_id)
);
CREATE INDEX idx_content_reports_content ON content_reports (content_type, content_id);

-- Takedowns are a visibility marker, never a delete: appeals and evidence need the row.
CREATE TABLE content_takedowns (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_type VARCHAR(40) NOT NULL,
    content_id   UUID NOT NULL,
    owner_id     UUID,
    category     VARCHAR(40),
    reason       TEXT NOT NULL,
    removed_by   UUID,
    removed_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    restored_by  UUID,
    restored_at  TIMESTAMP,
    CONSTRAINT uq_content_takedown_active UNIQUE (content_type, content_id)
);
CREATE INDEX idx_content_takedowns_lookup ON content_takedowns (content_type, content_id) WHERE restored_at IS NULL;

-- Per-image moderation outcome, kept for appeals (MOD-04) and for MOD-08 ranking.
CREATE TABLE image_moderation_results (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    object_key    VARCHAR(500),
    checksum      VARCHAR(64),
    user_id       UUID,
    entry_point   VARCHAR(60) NOT NULL,
    decision      VARCHAR(20) NOT NULL,
    category      VARCHAR(40),
    confidence    NUMERIC(5, 4),
    provider      VARCHAR(40),
    raw_scores    JSONB,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_image_moderation_created ON image_moderation_results (created_at DESC);
CREATE INDEX idx_image_moderation_checksum ON image_moderation_results (checksum);
