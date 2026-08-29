-- PAS-01 seed: the official 63-province list, dataset version 2008-63.
--
-- The version is recorded on every row on purpose. Administrative boundaries change, and
-- two backfill runs against two different lists would otherwise produce two different
-- answers to "how many provinces have I visited" with nothing to explain the difference.
--
-- `aliases` holds the already-normalized spellings that must resolve to the row: with and
-- without the "tinh"/"thanh pho" prefix, and the everyday names people actually type.
-- Matching on the free-text address alone is why this column exists.

INSERT INTO provinces (code, name, normalized_name, region, aliases, dataset_version) VALUES
    ('01', 'Hà Nội', 'ha noi', 'NORTH', '["ha noi","tp ha noi","thanh pho ha noi","hanoi"]', '2008-63'),
    ('02', 'Hà Giang', 'ha giang', 'NORTH', '["ha giang","tinh ha giang"]', '2008-63'),
    ('04', 'Cao Bằng', 'cao bang', 'NORTH', '["cao bang","tinh cao bang"]', '2008-63'),
    ('06', 'Bắc Kạn', 'bac kan', 'NORTH', '["bac kan","bac can","tinh bac kan"]', '2008-63'),
    ('08', 'Tuyên Quang', 'tuyen quang', 'NORTH', '["tuyen quang","tinh tuyen quang"]', '2008-63'),
    ('10', 'Lào Cai', 'lao cai', 'NORTH', '["lao cai","tinh lao cai","sa pa","sapa"]', '2008-63'),
    ('11', 'Điện Biên', 'dien bien', 'NORTH', '["dien bien","tinh dien bien","dien bien phu"]', '2008-63'),
    ('12', 'Lai Châu', 'lai chau', 'NORTH', '["lai chau","tinh lai chau"]', '2008-63'),
    ('14', 'Sơn La', 'son la', 'NORTH', '["son la","tinh son la","moc chau"]', '2008-63'),
    ('15', 'Yên Bái', 'yen bai', 'NORTH', '["yen bai","tinh yen bai","mu cang chai"]', '2008-63'),
    ('17', 'Hòa Bình', 'hoa binh', 'NORTH', '["hoa binh","tinh hoa binh","mai chau"]', '2008-63'),
    ('19', 'Thái Nguyên', 'thai nguyen', 'NORTH', '["thai nguyen","tinh thai nguyen"]', '2008-63'),
    ('20', 'Lạng Sơn', 'lang son', 'NORTH', '["lang son","tinh lang son"]', '2008-63'),
    ('22', 'Quảng Ninh', 'quang ninh', 'NORTH', '["quang ninh","tinh quang ninh","ha long","halong"]', '2008-63'),
    ('24', 'Bắc Giang', 'bac giang', 'NORTH', '["bac giang","tinh bac giang"]', '2008-63'),
    ('25', 'Phú Thọ', 'phu tho', 'NORTH', '["phu tho","tinh phu tho"]', '2008-63'),
    ('26', 'Vĩnh Phúc', 'vinh phuc', 'NORTH', '["vinh phuc","tinh vinh phuc","tam dao"]', '2008-63'),
    ('27', 'Bắc Ninh', 'bac ninh', 'NORTH', '["bac ninh","tinh bac ninh"]', '2008-63'),
    ('30', 'Hải Dương', 'hai duong', 'NORTH', '["hai duong","tinh hai duong"]', '2008-63'),
    ('31', 'Hải Phòng', 'hai phong', 'NORTH', '["hai phong","tp hai phong","thanh pho hai phong","cat ba"]', '2008-63'),
    ('33', 'Hưng Yên', 'hung yen', 'NORTH', '["hung yen","tinh hung yen"]', '2008-63'),
    ('34', 'Thái Bình', 'thai binh', 'NORTH', '["thai binh","tinh thai binh"]', '2008-63'),
    ('35', 'Hà Nam', 'ha nam', 'NORTH', '["ha nam","tinh ha nam"]', '2008-63'),
    ('36', 'Nam Định', 'nam dinh', 'NORTH', '["nam dinh","tinh nam dinh"]', '2008-63'),
    ('37', 'Ninh Bình', 'ninh binh', 'NORTH', '["ninh binh","tinh ninh binh","trang an","tam coc"]', '2008-63'),
    ('38', 'Thanh Hóa', 'thanh hoa', 'CENTRAL', '["thanh hoa","tinh thanh hoa","sam son"]', '2008-63'),
    ('40', 'Nghệ An', 'nghe an', 'CENTRAL', '["nghe an","tinh nghe an","vinh"]', '2008-63'),
    ('42', 'Hà Tĩnh', 'ha tinh', 'CENTRAL', '["ha tinh","tinh ha tinh"]', '2008-63'),
    ('44', 'Quảng Bình', 'quang binh', 'CENTRAL', '["quang binh","tinh quang binh","phong nha","dong hoi"]', '2008-63'),
    ('45', 'Quảng Trị', 'quang tri', 'CENTRAL', '["quang tri","tinh quang tri"]', '2008-63'),
    ('46', 'Thừa Thiên Huế', 'thua thien hue', 'CENTRAL', '["thua thien hue","tinh thua thien hue","hue","tp hue"]', '2008-63'),
    ('48', 'Đà Nẵng', 'da nang', 'CENTRAL', '["da nang","tp da nang","thanh pho da nang","danang"]', '2008-63'),
    ('49', 'Quảng Nam', 'quang nam', 'CENTRAL', '["quang nam","tinh quang nam","hoi an","my son"]', '2008-63'),
    ('51', 'Quảng Ngãi', 'quang ngai', 'CENTRAL', '["quang ngai","tinh quang ngai","ly son"]', '2008-63'),
    ('52', 'Bình Định', 'binh dinh', 'CENTRAL', '["binh dinh","tinh binh dinh","quy nhon"]', '2008-63'),
    ('54', 'Phú Yên', 'phu yen', 'CENTRAL', '["phu yen","tinh phu yen","tuy hoa"]', '2008-63'),
    ('56', 'Khánh Hòa', 'khanh hoa', 'CENTRAL', '["khanh hoa","tinh khanh hoa","nha trang","cam ranh"]', '2008-63'),
    ('58', 'Ninh Thuận', 'ninh thuan', 'CENTRAL', '["ninh thuan","tinh ninh thuan","phan rang"]', '2008-63'),
    ('60', 'Bình Thuận', 'binh thuan', 'CENTRAL', '["binh thuan","tinh binh thuan","mui ne","phan thiet"]', '2008-63'),
    ('62', 'Kon Tum', 'kon tum', 'CENTRAL', '["kon tum","tinh kon tum"]', '2008-63'),
    ('64', 'Gia Lai', 'gia lai', 'CENTRAL', '["gia lai","tinh gia lai","pleiku"]', '2008-63'),
    ('66', 'Đắk Lắk', 'dak lak', 'CENTRAL', '["dak lak","tinh dak lak","daklak","buon ma thuot"]', '2008-63'),
    ('67', 'Đắk Nông', 'dak nong', 'CENTRAL', '["dak nong","tinh dak nong","daknong","gia nghia"]', '2008-63'),
    ('68', 'Lâm Đồng', 'lam dong', 'CENTRAL', '["lam dong","tinh lam dong","da lat","dalat"]', '2008-63'),
    ('70', 'Bình Phước', 'binh phuoc', 'SOUTH', '["binh phuoc","tinh binh phuoc"]', '2008-63'),
    ('72', 'Tây Ninh', 'tay ninh', 'SOUTH', '["tay ninh","tinh tay ninh","nui ba den"]', '2008-63'),
    ('74', 'Bình Dương', 'binh duong', 'SOUTH', '["binh duong","tinh binh duong"]', '2008-63'),
    ('75', 'Đồng Nai', 'dong nai', 'SOUTH', '["dong nai","tinh dong nai","bien hoa"]', '2008-63'),
    ('77', 'Bà Rịa - Vũng Tàu', 'ba ria vung tau', 'SOUTH', '["ba ria vung tau","tinh ba ria vung tau","vung tau","con dao"]', '2008-63'),
    ('79', 'Hồ Chí Minh', 'ho chi minh', 'SOUTH', '["ho chi minh","tp ho chi minh","thanh pho ho chi minh","tphcm","hcm","sai gon","saigon"]', '2008-63'),
    ('80', 'Long An', 'long an', 'SOUTH', '["long an","tinh long an"]', '2008-63'),
    ('82', 'Tiền Giang', 'tien giang', 'SOUTH', '["tien giang","tinh tien giang","my tho"]', '2008-63'),
    ('83', 'Bến Tre', 'ben tre', 'SOUTH', '["ben tre","tinh ben tre"]', '2008-63'),
    ('84', 'Trà Vinh', 'tra vinh', 'SOUTH', '["tra vinh","tinh tra vinh"]', '2008-63'),
    ('86', 'Vĩnh Long', 'vinh long', 'SOUTH', '["vinh long","tinh vinh long"]', '2008-63'),
    ('87', 'Đồng Tháp', 'dong thap', 'SOUTH', '["dong thap","tinh dong thap","cao lanh"]', '2008-63'),
    ('89', 'An Giang', 'an giang', 'SOUTH', '["an giang","tinh an giang","chau doc","long xuyen"]', '2008-63'),
    ('91', 'Kiên Giang', 'kien giang', 'SOUTH', '["kien giang","tinh kien giang","phu quoc","rach gia"]', '2008-63'),
    ('92', 'Cần Thơ', 'can tho', 'SOUTH', '["can tho","tp can tho","thanh pho can tho"]', '2008-63'),
    ('93', 'Hậu Giang', 'hau giang', 'SOUTH', '["hau giang","tinh hau giang"]', '2008-63'),
    ('94', 'Sóc Trăng', 'soc trang', 'SOUTH', '["soc trang","tinh soc trang"]', '2008-63'),
    ('95', 'Bạc Liêu', 'bac lieu', 'SOUTH', '["bac lieu","tinh bac lieu"]', '2008-63'),
    ('96', 'Cà Mau', 'ca mau', 'SOUTH', '["ca mau","tinh ca mau"]', '2008-63')
ON CONFLICT (code) DO NOTHING;

-- PAS-04 seed. Seven easy rules on purpose: an achievement system nobody can reach at the
-- start is one nobody notices. Rules that do not depend on province data come first,
-- because province coverage is the longest pole and the least accurate at launch.
INSERT INTO passport_stamp_rules
    (code, version, name, description, condition_type, threshold, icon, reward_points) VALUES
    ('FIRST_STEP', 1, 'Bước chân đầu tiên', 'Check-in lần đầu tiên', 'FIRST_CHECKIN', 1, 'footprint', 10),
    ('EXPLORER_5', 1, 'Người khám phá', 'Check-in ở 5 nơi khác nhau', 'DISTINCT_PLACE_COUNT', 5, 'compass', 20),
    ('EXPLORER_25', 1, 'Nhà thám hiểm', 'Check-in ở 25 nơi khác nhau', 'DISTINCT_PLACE_COUNT', 25, 'map', 50),
    ('STORYTELLER_10', 1, 'Người kể chuyện', 'Có 10 lần check-in', 'CHECKIN_COUNT', 10, 'book', 20),
    ('TRUSTED_10', 1, 'Dấu chân xác thực', 'Có 10 check-in được xác thực vị trí', 'VERIFIED_CHECKIN_COUNT', 10, 'shield', 30),
    ('PROVINCE_3', 1, 'Ba miền một chuyến', 'Đã đến 3 tỉnh/thành', 'DISTINCT_PROVINCE_COUNT', 3, 'flag', 30),
    ('PROVINCE_10', 1, 'Mười tỉnh thành', 'Đã đến 10 tỉnh/thành', 'DISTINCT_PROVINCE_COUNT', 10, 'flag-star', 80)
ON CONFLICT (code, version) DO NOTHING;

-- Admin permissions for the screens these epics add.
INSERT INTO admin_permissions(id, resource, action)
SELECT md5(resource || ':' || action)::uuid, resource, action
FROM (VALUES
    ('checkin-clusters', 'get'),
    ('checkin-clusters', 'update'),
    ('passport-rewards', 'get'),
    ('passport-rewards', 'create'),
    ('passport-rewards', 'update'),
    ('provinces', 'get'),
    ('provinces', 'update'),
    ('trust-roles', 'get'),
    ('trust-roles', 'update'),
    ('points', 'get'),
    ('points', 'update'),
    ('guides', 'get'),
    ('guides', 'update')
) AS p(resource, action)
ON CONFLICT (resource, action) DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.resource IN
    ('checkin-clusters', 'passport-rewards', 'provinces', 'trust-roles', 'points', 'guides')
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;
