-- Image moderation moves out of the upload request. The upload stores the image, records a
-- PENDING row and returns; a background worker scores the image and completes the row.
-- A blocked image is hidden through content_takedowns (content_type 'IMAGE', content_id =
-- this row's id) and the API replaces its URL on the way out.
ALTER TABLE image_moderation_results
    ADD COLUMN url          VARCHAR(1000),
    ADD COLUMN mime_type    VARCHAR(40),
    ADD COLUMN status       VARCHAR(20) NOT NULL DEFAULT 'DONE',
    ADD COLUMN completed_at TIMESTAMP,
    ALTER COLUMN decision DROP NOT NULL;

ALTER TABLE image_moderation_results
    ADD CONSTRAINT ck_image_moderation_status CHECK (status IN ('PENDING', 'DONE'));

-- The retry job polls rows a restart left unfinished.
CREATE INDEX idx_image_moderation_pending
    ON image_moderation_results (created_at) WHERE status = 'PENDING';
