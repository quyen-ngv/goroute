-- Image compression across every storage group (epic 00 §9b). Compression overwrites each
-- file under its own key, so no URL anywhere changes; this table only remembers which keys were
-- already handled, so a later run or the upload hook never compresses a file twice.
-- ImageStorageCleanupService leaves this table out of its column scan: a key listed here is not
-- a reference to the file.
CREATE TABLE IF NOT EXISTS image_compressions (
    object_key       VARCHAR(512) PRIMARY KEY,
    -- ImageCompressionGroup
    group_code       VARCHAR(40)  NOT NULL,
    -- ImageCompressionSource: AUTO, BULK, PLACE
    source           VARCHAR(20)  NOT NULL,
    -- ImageCompressionOutcome: COMPRESSED, NO_SAVINGS, NOT_IMAGE
    outcome          VARCHAR(20)  NOT NULL,
    -- ImageCompressionTier: FREE, PRO
    tier             VARCHAR(10)  NOT NULL,
    quality          INT          NOT NULL,
    max_long_edge    INT          NOT NULL,
    original_bytes   BIGINT       NOT NULL,
    -- Equal to original_bytes unless COMPRESSED.
    compressed_bytes BIGINT       NOT NULL,
    compressed_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- The console's per-group totals.
CREATE INDEX IF NOT EXISTS idx_image_compressions_group ON image_compressions (group_code, outcome);

INSERT INTO config (label, key, value, description, is_active)
VALUES ('IMAGE_COMPRESSION', 'AUTO_ENABLED', 'false',
        'Nen anh sang WebP ngay sau khi upload (ghi de tai cho, khong doi URL). true/false', TRUE),
       ('IMAGE_COMPRESSION', 'QUALITY_FREE', '70',
        'Chat luong WebP cho anh cua user goi FREE (30-95)', TRUE),
       ('IMAGE_COMPRESSION', 'QUALITY_PRO', '82',
        'Chat luong WebP cho anh cua user PRO tro len, staff, partner va anh he thong (30-95)', TRUE),
       ('IMAGE_COMPRESSION', 'MAX_LONG_EDGE_FREE', '1600',
        'Canh dai toi da (px) cho anh cua user FREE; khong phong to (640-4096)', TRUE),
       ('IMAGE_COMPRESSION', 'MAX_LONG_EDGE_PRO', '2048',
        'Canh dai toi da (px) cho anh PRO tro len va anh he thong; khong phong to (640-4096)', TRUE),
       ('IMAGE_COMPRESSION', 'MIN_SAVINGS_PERCENT', '10',
        'Chi ghi de khi WebP nho hon it nhat bay nhieu phan tram (0-90)', TRUE)
ON CONFLICT (label, key) DO NOTHING;
