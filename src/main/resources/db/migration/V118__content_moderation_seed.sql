-- MOD-02/MOD-04/MOD-07: operational knobs and the hand-reviewed starter list.
-- Every value here is administrable so a threshold change never needs a release.

INSERT INTO config (label, key, value, description, is_active) VALUES
    ('MODERATION', 'TEXT_FILTER_ENABLED', 'true', 'Bat bo loc tu khoa dung chung', TRUE),
    ('MODERATION', 'AI_TEXT_ENABLED', 'false', 'Bat lop kiem duyet van ban bang AI cho noi dung cong khai', TRUE),
    ('MODERATION', 'AI_TEXT_MAX_LENGTH', '4000', 'Do dai toi da gui sang lop AI', TRUE),
    ('MODERATION', 'IMAGE_MODERATION_ENABLED', 'false', 'Bat kiem duyet noi dung anh', TRUE),
    ('MODERATION', 'IMAGE_THRESHOLD_SEXUAL', '0.85', 'Nguong tu choi anh khieu dam (0-1)', TRUE),
    ('MODERATION', 'IMAGE_THRESHOLD_VIOLENCE', '0.85', 'Nguong tu choi anh bao luc (0-1)', TRUE),
    ('MODERATION', 'IMAGE_THRESHOLD_DRUGS_WEAPONS', '0.90', 'Nguong tu choi anh ma tuy/vu khi (0-1)', TRUE),
    ('MODERATION', 'IMAGE_THRESHOLD_HATE_SYMBOL', '0.80', 'Nguong tu choi anh bieu tuong thu ghet (0-1)', TRUE),
    ('MODERATION', 'IMAGE_THRESHOLD_SPAM', '0.95', 'Nguong gan co anh rac/quang cao (0-1)', TRUE),
    ('MODERATION', 'STRICTNESS_PUBLIC', 'FULL', 'Muc nghiem ngat cho noi dung cong khai: FULL, KEYWORD_ONLY, REPORT_ONLY, OFF', TRUE),
    ('MODERATION', 'STRICTNESS_GROUP', 'KEYWORD_ONLY', 'Muc nghiem ngat cho noi dung trong nhom', TRUE),
    ('MODERATION', 'STRICTNESS_DIRECT', 'REPORT_ONLY', 'Muc nghiem ngat cho tin nhan rieng tu mot-mot', TRUE),
    ('MODERATION', 'STRICTNESS_PRIVATE', 'OFF', 'Muc nghiem ngat cho noi dung chi minh user xem', TRUE),
    ('MODERATION', 'POLICY_VERSION', '1.0.0', 'Phien ban chinh sach noi dung dang ap dung', TRUE)
ON CONFLICT (label, key) DO NOTHING;

-- Starter list. Deliberately small and mostly FLAG: a large imported list would block
-- honest reviews in week one. Operations grows it from the MOD-08 ranking.
INSERT INTO moderation_terms (term, normalized_term, category, action, language, is_exemption, note) VALUES
    ('phim sex',         'phim sex',         'SEXUAL',        'BLOCK', 'vi', FALSE, 'Khong co ngu canh du lich hop le'),
    ('gái gọi',          'gai goi',          'SEXUAL',        'BLOCK', 'vi', FALSE, 'Chao moi dich vu'),
    ('massage kích dục', 'massage kich duc', 'SEXUAL',        'BLOCK', 'vi', FALSE, 'Chao moi dich vu'),
    ('khỏa thân',        'khoa than',        'SEXUAL',        'FLAG',  'vi', FALSE, 'Co ngu canh nghe thuat hop le, xem mien tru'),
    ('máu me',           'mau me',           'VIOLENCE',      'FLAG',  'vi', FALSE, 'Thuong la cach noi, can nguoi xem'),
    ('vcl',              'vcl',              'HARASSMENT',    'FLAG',  'vi', FALSE, 'Viet tat tuc'),
    ('dcm',              'dcm',              'HARASSMENT',    'FLAG',  'vi', FALSE, 'Viet tat tuc'),
    ('đồ ngu',           'do ngu',           'HARASSMENT',    'FLAG',  'vi', FALSE, 'Xuc pham ca nhan'),
    ('thằng chó',        'thang cho',        'HARASSMENT',    'FLAG',  'vi', FALSE, 'Xuc pham ca nhan'),
    ('lừa đảo',          'lua dao',          'SCAM',          'FLAG',  'vi', FALSE, 'Rat hay xuat hien trong review that, khong duoc BLOCK'),
    ('chuyển khoản trước','chuyen khoan truoc','SCAM',        'FLAG',  'vi', FALSE, 'Dau hieu chao moi ngoai nen tang'),
    ('liên hệ zalo',     'lien he zalo',     'SPAM',          'FLAG',  'vi', FALSE, 'Keo giao dich ra ngoai nen tang'),
    ('kết bạn zalo',     'ket ban zalo',     'SPAM',          'FLAG',  'vi', FALSE, 'Keo giao dich ra ngoai nen tang'),
    ('số cmnd',          'so cmnd',          'PERSONAL_DATA', 'FLAG',  'vi', FALSE, 'Giay to tuy than cua nguoi khac'),
    ('số cccd',          'so cccd',          'PERSONAL_DATA', 'FLAG',  'vi', FALSE, 'Giay to tuy than cua nguoi khac'),
    ('bắc kỳ',           'bac ky',           'HATE_SPEECH',   'FLAG',  'vi', FALSE, 'Miet thi vung mien tuy ngu canh'),
    ('nam kỳ',           'nam ky',           'HATE_SPEECH',   'FLAG',  'vi', FALSE, 'Miet thi vung mien tuy ngu canh'),
    ('escort service',   'escort service',   'SEXUAL',        'BLOCK', 'en', FALSE, 'Chao moi dich vu'),
    ('scam',             'scam',             'SCAM',          'FLAG',  'en', FALSE, 'Hay dung trong review that')
ON CONFLICT (normalized_term, language, is_exemption) DO NOTHING;

-- Exemptions are evaluated first. Without these the filter blocks legitimate travel
-- and food writing in the first week, and every false block costs a user.
INSERT INTO moderation_terms (term, normalized_term, category, action, language, is_exemption, note) VALUES
    ('tượng khỏa thân', 'tuong khoa than', 'SEXUAL',   'ALLOW', 'vi', TRUE, 'Bao tang, tac pham dieu khac'),
    ('tranh khỏa thân', 'tranh khoa than', 'SEXUAL',   'ALLOW', 'vi', TRUE, 'Phong tranh'),
    ('bãi tắm tiên',    'bai tam tien',    'SEXUAL',   'ALLOW', 'vi', TRUE, 'Dia danh co that'),
    ('bún máu me',      'bun mau me',      'VIOLENCE', 'ALLOW', 'vi', TRUE, 'Ten mon an'),
    ('lừa đảo du lịch', 'lua dao du lich', 'SCAM',     'ALLOW', 'vi', TRUE, 'Cum canh bao chinh dang trong review'),
    ('scam alert',      'scam alert',      'SCAM',     'ALLOW', 'en', TRUE, 'Cum canh bao chinh dang trong review')
ON CONFLICT (normalized_term, language, is_exemption) DO NOTHING;

-- Admin permissions for the moderation console (MOD-02, MOD-06, MOD-08, SOC-06a).
INSERT INTO admin_permissions(id, resource, action)
SELECT md5(resource || ':' || action)::uuid, resource, action
FROM (VALUES
    ('moderation-terms', 'get'),
    ('moderation-terms', 'create'),
    ('moderation-terms', 'update'),
    ('moderation-terms', 'delete'),
    ('moderation-queue', 'get'),
    ('moderation-queue', 'update'),
    ('moderation-metrics', 'get')
) AS p(resource, action)
ON CONFLICT (resource, action) DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.resource IN ('moderation-terms', 'moderation-queue', 'moderation-metrics')
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;
