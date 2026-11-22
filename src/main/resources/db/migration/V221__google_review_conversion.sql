-- Google Maps reviews become check-ins + reviews authored by placeholder users.
-- See GoogleReviewConversionService and .agents/docs/07-reviews-rating.md §3.5.
--
-- Nothing here moves data. The conversion is an operator action
-- (POST /v1/api/admin/place-reviews/convert-to-checkins), not part of the deploy, because
-- it deletes rows and an operator has to dry-run it first.

-- One row per Google reviewer that now exists as a users row. The users row itself is
-- an anonymous account that cannot sign in: a generated animal name, no avatar, a
-- synthetic email under the reserved .invalid TLD (RFC 2606, so no real address can ever
-- equal it and registration never collides), no password, no provider id,
-- account_status DISABLED. This table is how the rest of the code tells such an account
-- apart (Discover, referral codes, dashboard counts), and how a second place reviewed by
-- the same person reuses the same account.
CREATE TABLE IF NOT EXISTS imported_reviewers (
    user_id     UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    source      VARCHAR(30)  NOT NULL,
    -- SHA-256 hex of "GOOGLE_MAPS:" + the contributor id from profile_url (/maps/contrib/<id>),
    -- or of "GOOGLE_MAPS:review:<google review id>" when the review had no profile link.
    -- Hashed so this table holds nothing that leads back to the person's Google profile.
    external_id VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_imported_reviewers_source_external UNIQUE (source, external_id),
    CONSTRAINT ck_imported_reviewers_source CHECK (source IN ('GOOGLE_MAPS'))
);

-- Every place_reviews row the conversion deletes is copied here first, kept or not, so the
-- text can be audited and restored row by row. converted_* is set on the rows that became
-- a check-in. The image files of the rows that did not are deleted from storage, so their
-- images/profile_picture URLs here no longer resolve.
CREATE TABLE IF NOT EXISTS place_reviews_archive (
    id                   UUID PRIMARY KEY,
    place_id             UUID NOT NULL,
    review_id            VARCHAR(255),
    google_place_id      VARCHAR(255),
    reviewer_name        VARCHAR(255),
    profile_url          VARCHAR(500),
    profile_picture      TEXT,
    is_local_guide       BOOLEAN,
    total_reviews        INTEGER,
    total_photos         INTEGER,
    rating               INTEGER,
    description          TEXT,
    language             VARCHAR(10),
    review_date          DATE,
    images               TEXT,
    likes                INTEGER,
    content_hash         VARCHAR(255),
    is_deleted           BOOLEAN,
    authenticity_score   NUMERIC(4, 3),
    authenticity_level   VARCHAR(10),
    score_calculated_at  TIMESTAMP,
    created_at           TIMESTAMP,
    updated_at           TIMESTAMP,
    converted_user_id    UUID,
    converted_checkin_id UUID,
    converted_review_id  UUID,
    archived_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_place_reviews_archive_place ON place_reviews_archive (place_id);

-- Converted places must not be refilled with Google reviews by the scraper worker's daily
-- run. Administrator-triggered refreshes and imports carrying userReviews still write
-- place_reviews; rerunning the conversion cleans those up.
UPDATE config
SET value = 'false'
WHERE label = 'PLACE_REVIEW' AND key = 'DAILY_REFRESH_ENABLED';
