-- Mở rộng danh sách từ khoá kiểm duyệt (tiếng Việt). Bổ sung cho starter list ở V118.
-- Quy ước (theo CONTENT_POLICY): chửi bới/xúc phạm → FLAG (người duyệt xem), chỉ mời gọi
-- mại dâm và nội dung tình dục rõ ràng → BLOCK. Chèn lệnh ON CONFLICT để không ghi đè
-- những gì admin đã sửa trên console.
--
-- Chỉ dùng CỤM TỪ cho các từ đơn dễ trùng từ vô hại không dấu: "cac" (các), "di" (đi),
-- "lon" (lớn), "du" (du lịch), "deo" (đeo), "dan" (dân), "ngu" (ngủ), "dm" (direct message).
-- Đừng thêm các từ đơn đó vào đây.

INSERT INTO moderation_terms (term, normalized_term, category, action, language, is_exemption, note) VALUES
    -- Mời gọi mại dâm / tình dục: BLOCK
    ('dịch vụ tình dục',     'dich vu tinh duc',    'SEXUAL',        'BLOCK', 'vi', FALSE, 'Chào mời dịch vụ tình dục'),
    ('phục vụ tình dục',     'phuc vu tinh duc',    'SEXUAL',        'BLOCK', 'vi', FALSE, 'Chào mời dịch vụ tình dục'),
    ('gái bao',              'gai bao',             'SEXUAL',        'BLOCK', 'vi', FALSE, 'Chào mời dịch vụ tình dục'),
    ('gọi gái',              'goi gai',             'SEXUAL',        'BLOCK', 'vi', FALSE, 'Chào mời dịch vụ tình dục'),
    ('sex tour',             'sex tour',            'SEXUAL',        'BLOCK', 'en', FALSE, 'Du lịch mại dâm'),
    ('hiếp dâm',             'hiep dam',            'SEXUAL',        'BLOCK', 'vi', FALSE, 'Nội dung bạo lực tình dục'),
    ('cưỡng hiếp',           'cuong hiep',          'SEXUAL',        'BLOCK', 'vi', FALSE, 'Nội dung bạo lực tình dục'),
    -- Nội dung tình dục, cần người duyệt: FLAG
    ('khiêu dâm',            'khieu dam',           'SEXUAL',        'FLAG',  'vi', FALSE, 'Nội dung khiêu dâm'),
    ('phim người lớn',       'phim nguoi lon',      'SEXUAL',        'FLAG',  'vi', FALSE, 'Quảng cáo nội dung người lớn'),
    ('ảnh nóng',             'anh nong',            'SEXUAL',        'FLAG',  'vi', FALSE, 'Ảnh tình dục'),
    ('clip nóng',            'clip nong',           'SEXUAL',        'FLAG',  'vi', FALSE, 'Clip tình dục'),
    ('onlyfans',             'onlyfans',            'SEXUAL',        'FLAG',  'en', FALSE, 'Nền tảng nội dung người lớn'),
    ('xxx',                  'xxx',                 'SEXUAL',        'FLAG',  'en', FALSE, 'Nội dung người lớn'),
    ('sex',                  'sex',                 'SEXUAL',        'FLAG',  'en', FALSE, 'Thường là nội dung người lớn'),
    -- Chửi bới, xúc phạm cá nhân: FLAG
    ('đéo biết',             'deo biet',            'HARASSMENT',    'FLAG',  'vi', FALSE, 'Chửi tục'),
    ('đéo gì',               'deo gi',              'HARASSMENT',    'FLAG',  'vi', FALSE, 'Chửi tục'),
    ('đéo hiểu',             'deo hieu',            'HARASSMENT',    'FLAG',  'vi', FALSE, 'Chửi tục'),
    ('địt mẹ',               'dit me',              'HARASSMENT',    'FLAG',  'vi', FALSE, 'Chửi tục'),
    ('địt con mẹ',           'dit con me',          'HARASSMENT',    'FLAG',  'vi', FALSE, 'Chửi tục'),
    ('đụ má',                'du ma',               'HARASSMENT',    'FLAG',  'vi', FALSE, 'Chửi tục'),
    ('đmm',                  'dmm',                 'HARASSMENT',    'FLAG',  'vi', FALSE, 'Viết tắt chửi tục'),
    ('clgt',                 'clgt',                'HARASSMENT',    'FLAG',  'vi', FALSE, 'Viết tắt chửi tục'),
    ('vkl',                  'vkl',                 'HARASSMENT',    'FLAG',  'vi', FALSE, 'Viết tắt chửi tục'),
    ('thằng cặc',            'thang cac',           'HARASSMENT',    'FLAG',  'vi', FALSE, 'Xúc phạm cá nhân'),
    ('con cặc',              'con cac',             'HARASSMENT',    'FLAG',  'vi', FALSE, 'Xúc phạm cá nhân'),
    ('con lồn',              'con lon',             'HARASSMENT',    'FLAG',  'vi', FALSE, 'Chửi tục'),
    ('đồ lồn',               'do lon',              'HARASSMENT',    'FLAG',  'vi', FALSE, 'Chửi tục'),
    ('đĩ đực',               'di duc',              'HARASSMENT',    'FLAG',  'vi', FALSE, 'Xúc phạm cá nhân'),
    ('đĩ thõa',              'di thoa',             'HARASSMENT',    'FLAG',  'vi', FALSE, 'Xúc phạm cá nhân'),
    ('con điếm',             'con diem',            'HARASSMENT',    'FLAG',  'vi', FALSE, 'Xúc phạm cá nhân'),
    ('mất dạy',              'mat day',             'HARASSMENT',    'FLAG',  'vi', FALSE, 'Xúc phạm cá nhân'),
    ('súc sinh',             'suc sinh',            'HARASSMENT',    'FLAG',  'vi', FALSE, 'Xúc phạm cá nhân'),
    ('óc chó',               'oc cho',              'HARASSMENT',    'FLAG',  'vi', FALSE, 'Xúc phạm cá nhân'),
    ('đồ chó',               'do cho',              'HARASSMENT',    'FLAG',  'vi', FALSE, 'Xúc phạm cá nhân'),
    ('thằng ngu',            'thang ngu',           'HARASSMENT',    'FLAG',  'vi', FALSE, 'Xúc phạm cá nhân'),
    ('ngu như bò',           'ngu nhu bo',          'HARASSMENT',    'FLAG',  'vi', FALSE, 'Xúc phạm cá nhân'),
    ('khốn nạn',             'khon nan',            'HARASSMENT',    'FLAG',  'vi', FALSE, 'Xúc phạm cá nhân'),
    ('mẹ mày',               'me may',              'HARASSMENT',    'FLAG',  'vi', FALSE, 'Xúc phạm, cần ngữ cảnh'),
    ('thằng khùng',          'thang khung',         'HARASSMENT',    'FLAG',  'vi', FALSE, 'Xúc phạm cá nhân'),
    ('con điên',             'con dien',            'HARASSMENT',    'FLAG',  'vi', FALSE, 'Xúc phạm cá nhân'),
    ('câm mồm',              'cam mom',             'HARASSMENT',    'FLAG',  'vi', FALSE, 'Lời lẽ thô lỗ'),
    ('im mồm',               'im mom',              'HARASSMENT',    'FLAG',  'vi', FALSE, 'Lời lẽ thô lỗ'),
    ('bố láo',               'bo lao',              'HARASSMENT',    'FLAG',  'vi', FALSE, 'Lời lẽ thô lỗ'),
    ('cút đi',               'cut di',              'HARASSMENT',    'FLAG',  'vi', FALSE, 'Lời lẽ thô lỗ'),
    ('fuck',                 'fuck',                'HARASSMENT',    'FLAG',  'en', FALSE, 'Chửi tục'),
    ('shit',                 'shit',                'HARASSMENT',    'FLAG',  'en', FALSE, 'Chửi tục'),
    ('bitch',                'bitch',               'HARASSMENT',    'FLAG',  'en', FALSE, 'Xúc phạm'),
    ('idiot',                'idiot',               'HARASSMENT',    'FLAG',  'en', FALSE, 'Xúc phạm'),
    -- Ma tuý, vũ khí: FLAG
    ('ma túy',               'ma tuy',              'DRUGS_WEAPONS', 'FLAG',  'vi', FALSE, 'Mua bán ma tuý'),
    ('cần sa',               'can sa',              'DRUGS_WEAPONS', 'FLAG',  'vi', FALSE, 'Mua bán ma tuý'),
    ('thuốc lắc',            'thuoc lac',           'DRUGS_WEAPONS', 'FLAG',  'vi', FALSE, 'Mua bán ma tuý'),
    ('bán súng',             'ban sung',            'DRUGS_WEAPONS', 'FLAG',  'vi', FALSE, 'Mua bán vũ khí'),
    ('heroin',               'heroin',              'DRUGS_WEAPONS', 'FLAG',  'en', FALSE, 'Ma tuý'),
    ('cocaine',              'cocaine',             'DRUGS_WEAPONS', 'FLAG',  'en', FALSE, 'Ma tuý'),
    -- Bạo lực: FLAG
    ('giết người',           'giet nguoi',          'VIOLENCE',      'FLAG',  'vi', FALSE, 'Đe doạ hoặc mô tả bạo lực'),
    ('đánh chết',            'danh chet',           'VIOLENCE',      'FLAG',  'vi', FALSE, 'Đe doạ hoặc mô tả bạo lực'),
    -- Lừa đảo, cờ bạc, spam: SCAM / SPAM, FLAG
    ('inbox để nhận',        'inbox de nhan',       'SPAM',          'FLAG',  'vi', FALSE, 'Kéo giao dịch ra ngoài'),
    ('nhắn tin để nhận',     'nhan tin de nhan',    'SPAM',          'FLAG',  'vi', FALSE, 'Kéo giao dịch ra ngoài'),
    ('kiếm tiền online',     'kiem tien online',    'SPAM',          'FLAG',  'vi', FALSE, 'Chiêu trò kiếm tiền'),
    ('tuyển cộng tác viên',  'tuyen cong tac vien', 'SPAM',          'FLAG',  'vi', FALSE, 'Chiêu trò tuyển dụng'),
    ('việc nhẹ lương cao',   'viec nhe luong cao',  'SCAM',          'FLAG',  'vi', FALSE, 'Chiêu trò việc làm'),
    ('cho vay nặng lãi',     'cho vay nang lai',    'SCAM',          'FLAG',  'vi', FALSE, 'Cho vay nặng lãi'),
    ('vay tiền nhanh',       'vay tien nhanh',      'SCAM',          'FLAG',  'vi', FALSE, 'Quảng cáo vay tiền'),
    ('đầu tư lãi suất cao',  'dau tu lai suat cao', 'SCAM',          'FLAG',  'vi', FALSE, 'Chiêu trò đầu tư'),
    ('đánh bạc',             'danh bac',            'SPAM',          'FLAG',  'vi', FALSE, 'Cờ bạc'),
    ('cá độ',                'ca do',               'SPAM',          'FLAG',  'vi', FALSE, 'Cờ bạc'),
    ('soi kèo',              'soi keo',             'SPAM',          'FLAG',  'vi', FALSE, 'Cờ bạc'),
    ('nổ hũ',                'no hu',               'SPAM',          'FLAG',  'vi', FALSE, 'Cờ bạc'),
    ('free bitcoin',         'free bitcoin',        'SCAM',          'FLAG',  'en', FALSE, 'Lừa đảo tiền số'),
    ('double your money',    'double your money',   'SCAM',          'FLAG',  'en', FALSE, 'Lừa đảo'),
    ('send me money',        'send me money',       'SCAM',          'FLAG',  'en', FALSE, 'Lừa đảo'),
    ('gambling',             'gambling',            'SPAM',          'FLAG',  'en', FALSE, 'Cờ bạc')
ON CONFLICT (normalized_term, language, is_exemption) DO NOTHING;
