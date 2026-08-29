-- SOC-04: a shareable collection of places the user has saved.
--
-- A collection references saved items; it never copies the saved list. Sharing one
-- collection must not reveal anything else the user has saved, because the saved list is
-- private data and people save places they would not want listed.
CREATE TABLE place_collections (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name           VARCHAR(200) NOT NULL,
    description    TEXT,
    cover_image_url VARCHAR(1000),
    visibility     VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    -- Unguessable, and revoked by un-publishing: un-publishing has to mean the old link
    -- stops working, not merely that the collection leaves a list.
    share_slug     VARCHAR(40) UNIQUE,
    item_count     INT NOT NULL DEFAULT 0,
    view_count     INT NOT NULL DEFAULT 0,
    is_removed     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_collection_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE'))
);
CREATE INDEX idx_place_collections_owner ON place_collections (owner_id, updated_at DESC);
CREATE INDEX idx_place_collections_public ON place_collections (visibility, updated_at DESC)
    WHERE visibility = 'PUBLIC' AND is_removed = FALSE;

CREATE TABLE place_collection_items (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    collection_id UUID NOT NULL REFERENCES place_collections (id) ON DELETE CASCADE,
    place_id      UUID REFERENCES places (id) ON DELETE CASCADE,
    -- A collection can also hold a place the user typed themselves, which has no
    -- catalogue row; the display fields are then the only thing available.
    display_name  VARCHAR(300) NOT NULL,
    display_note  TEXT,
    latitude      NUMERIC(10, 7),
    longitude     NUMERIC(10, 7),
    -- Order is part of the content: "top 10" means something a random list does not.
    position      SMALLINT NOT NULL DEFAULT 0,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_collection_place UNIQUE (collection_id, place_id)
);
CREATE INDEX idx_collection_items ON place_collection_items (collection_id, position);

-- Operator-visible values for the keys registered in BusinessConfigKey. Seeding them is
-- what makes them adjustable without a release; the code default stays as the fallback if
-- a row is removed or set to something out of range.
INSERT INTO config (label, key, value, description, is_active) VALUES
    ('CHECKIN', 'CHECKIN_ENABLED', 'true', 'Bat tinh nang check-in', TRUE),
    ('CHECKIN', 'VERIFY_RADIUS_METERS', '200', 'Ban kinh xac thuc check-in (met, 20..5000)', TRUE),
    ('CHECKIN', 'MAX_ACCURACY_METERS', '100', 'Do chinh xac GPS toi da chap nhan (met, 5..2000)', TRUE),
    ('CHECKIN', 'MAX_PHOTOS', '6', 'So anh toi da moi check-in (1..20)', TRUE),
    ('CHECKIN', 'MAX_CAPTION_LENGTH', '2000', 'Do dai toi da cua cam nghi (100..5000)', TRUE),
    ('CHECKIN', 'LOCATION_KEY_PRECISION', '4', 'So chu so thap phan khi lam tron toa do de gom cum (2..6)', TRUE),
    ('CHECKIN', 'GALLERY_ALLOWED_FOR_FREE', 'true', 'Cho user FREE chon anh tu thu vien', TRUE),
    ('CHECKIN', 'GUIDE_SCREEN_ENABLED', 'true', 'Bat man hinh huong dan truoc khi chup', TRUE),
    ('CHECKIN', 'GUIDE_SCREEN_MAX_VIEWS', '3', 'So lan hien man hinh huong dan (0..50)', TRUE),
    ('CHECKIN', 'REWARD_ENABLED', 'true', 'Bat tinh diem thuong cho check-in', TRUE),
    ('CHECKIN', 'REWARD_BASE_POINTS', '5', 'Diem co ban moi check-in (0..500)', TRUE),
    ('CHECKIN', 'REWARD_CAMERA_MULTIPLIER', '1.0', 'He so diem khi chup truc tiep (0..5)', TRUE),
    ('CHECKIN', 'REWARD_GALLERY_MULTIPLIER', '0.4', 'He so diem khi chon anh tu thu vien (0..5)', TRUE),
    ('CHECKIN', 'REWARD_UNVERIFIED_MULTIPLIER', '0.5', 'He so diem khi chua xac thuc vi tri (0..5)', TRUE),
    ('CHECKIN', 'REWARD_DAILY_CAP', '50', 'Tran diem check-in moi ngay (0..5000)', TRUE),
    ('CHECKIN', 'CLUSTER_MIN_USERS', '3', 'So user toi thieu de mot cum hien trong hang doi (1..100)', TRUE),
    ('CHECKIN', 'CLUSTER_MIN_CHECKINS', '5', 'So check-in toi thieu de mot cum hien trong hang doi (1..500)', TRUE),
    ('PASSPORT', 'PASSPORT_ENABLED', 'true', 'Bat tinh nang Passport', TRUE),
    ('PASSPORT', 'PROVINCE_COVERAGE_THRESHOLD', '95', 'Do phu du lieu tinh toi thieu de cong bo phan tram (0..100)', TRUE),
    ('PASSPORT', 'TOTAL_PROVINCES', '63', 'Tong so tinh/thanh trong danh sach chinh thuc', TRUE),
    ('POINTS', 'EXPIRY_DAYS', '0', 'So ngay diem het han, 0 la khong het han (0..3650)', TRUE),
    ('GUIDE', 'GUIDE_ENABLED', 'false', 'Bat cho VietdeGuide', TRUE),
    ('GUIDE', 'PLATFORM_FEE_PERCENT', '20.0', 'Phi nen tang tren moi giao dich guide (%, 0..100)', TRUE),
    ('GUIDE', 'FEE_RULE_VERSION', '2026.08', 'Phien ban quy tac phi dang ap dung cho don moi', TRUE),
    ('GUIDE', 'RESPONSE_DEADLINE_HOURS', '48', 'Thoi han guide phai phan hoi don (gio, 1..720)', TRUE),
    ('GUIDE', 'PAYOUT_HOLD_DAYS', '7', 'So ngay giu tien sau khi hoan thanh truoc khi chi tra (0..90)', TRUE),
    ('GUIDE', 'MIN_REVIEWS_TO_SHOW_RATING', '3', 'So danh gia toi thieu truoc khi hien diem trung binh (1..100)', TRUE),
    ('GUIDE', 'FREE_SERVICE_LIMIT', '3', 'So dich vu duoc dang o goi Free (1..100)', TRUE),
    ('GUIDE', 'PREMIUM_MONTHLY_PRICE_VND', '299000', 'Gia goi Premium theo thang (VND)', TRUE),
    ('GUIDE', 'PREMIUM_YEARLY_PRICE_VND', '2990000', 'Gia goi Premium theo nam (VND)', TRUE)
ON CONFLICT (label, key) DO NOTHING;
