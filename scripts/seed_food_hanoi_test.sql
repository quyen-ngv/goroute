-- =============================================================================
-- Seed Food Discovery â€” HÃ  Ná»™i (test)
-- Cháº¡y sau migration V042. CÃ³ thá»ƒ cháº¡y láº¡i (idempotent theo UUID / place_id).
--
--   psql -U ... -d ... -f goroute/scripts/seed_food_hanoi_test.sql
--
-- Sau seed: náº¿u GET /v1/api/foods?citySlug=hanoi váº«n [] â€” restart backend
-- (cache foodsByCity 1h cÃ³ thá»ƒ Ä‘Ã£ lÆ°u [] trÆ°á»›c khi seed; báº£n má»›i khÃ´ng cache rá»—ng).
-- Hoáº·c gá»i PUT báº¥t ká»³ mÃ³n qua /v1/api/admin/foods/{id} Ä‘á»ƒ xÃ³a cache.
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1) MÃ³n Äƒn (foods)
-- -----------------------------------------------------------------------------
INSERT INTO foods (id, name_vi, name_en, description, category, image_url, created_at, updated_at)
VALUES
    (
        'a1000001-0001-4000-8000-000000000001',
        'Phá»Ÿ',
        'Pho',
        'NÆ°á»›c dÃ¹ng trong, thÆ¡m quáº¿ há»“i, bÃ¡nh phá»Ÿ má»m â€” mÃ³n biá»ƒu tÆ°á»£ng cá»§a HÃ  Ná»™i.',
        'NOODLE',
        'https://images.unsplash.com/photo-1591814468924-caf88d1232e1?w=400',
        NOW(), NOW()
    ),
    (
        'a1000001-0001-4000-8000-000000000002',
        'BÃºn cháº£',
        'Bun cha',
        'Thá»‹t nÆ°á»›ng than hoa, nÆ°á»›c máº¯m chua ngá»t, bÃºn vÃ  rau sá»‘ng.',
        'NOODLE',
        'https://images.unsplash.com/photo-1559339352-11d035aa65de?w=400',
        NOW(), NOW()
    ),
    (
        'a1000001-0001-4000-8000-000000000003',
        'BÃ¡nh cuá»‘n',
        'Banh cuon',
        'BÃ¡nh má»ng nhÃ¢n thá»‹t, cháº¥m nÆ°á»›c máº¯m pha, Äƒn nÃ³ng buá»•i sÃ¡ng.',
        'NOODLE',
        'https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=400',
        NOW(), NOW()
    ),
    (
        'a1000001-0001-4000-8000-000000000004',
        'CÃ  phÃª trá»©ng',
        'Egg coffee',
        'CÃ  phÃª Ä‘en phá»§ lá»›p kem trá»©ng bÃ©o ngáº­y â€” Ä‘áº·c sáº£n phá»‘ cá»•.',
        'DRINK',
        'https://images.unsplash.com/photo-1514434753793-5c31e2a088e6?w=400',
        NOW(), NOW()
    )
ON CONFLICT (id) DO UPDATE SET
    name_vi = EXCLUDED.name_vi,
    name_en = EXCLUDED.name_en,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    image_url = EXCLUDED.image_url,
    updated_at = NOW();

-- -----------------------------------------------------------------------------
-- 2) Äiá»ƒm theo thÃ nh phá»‘ (food_city_scores) â€” city_slug = hanoi, score >= 50
-- -----------------------------------------------------------------------------
INSERT INTO food_city_scores (id, food_id, city_slug, score, local_description, flavor_profile, fun_fact, created_at, updated_at)
VALUES
    (
        'c3000001-0001-4000-8000-000000000001',
        'a1000001-0001-4000-8000-000000000001',
        'hanoi',
        95,
        'Phá»Ÿ HÃ  Ná»™i: nÆ°á»›c trong, Ã­t ngá»t, hay Äƒn sÃ¡ng vá»›i quáº©y.',
        '{"sweet": 1, "salty": 3, "sour": 1, "spicy": 1, "rich": 3}'::jsonb,
        'Phá»Ÿ Ä‘Æ°á»£c nhiá»u ngÆ°á»i coi lÃ  linh há»“n áº©m thá»±c thá»§ Ä‘Ã´.',
        NOW(), NOW()
    ),
    (
        'c3000001-0001-4000-8000-000000000002',
        'a1000001-0001-4000-8000-000000000002',
        'hanoi',
        92,
        'BÃºn cháº£ Obama tá»«ng ghÃ© â€” Äƒn trÆ°a lÃ  chuáº©n nháº¥t.',
        '{"sweet": 2, "salty": 3, "sour": 2, "spicy": 2, "rich": 3}'::jsonb,
        'MÃ³n Ä‘Æ°á»£c Tá»•ng thá»‘ng Obama thá»­ khi tá»›i Viá»‡t Nam nÄƒm 2016.',
        NOW(), NOW()
    ),
    (
        'c3000001-0001-4000-8000-000000000003',
        'a1000001-0001-4000-8000-000000000003',
        'hanoi',
        78,
        'ThÆ°á»ng gáº·p á»Ÿ cÃ¡c hÃ ng bÃ¡nh cuá»‘n phá»‘ Äá»“ng XuÃ¢n, Thá»¥y KhuÃª.',
        '{"sweet": 1, "salty": 2, "sour": 1, "spicy": 0, "rich": 2}'::jsonb,
        NULL,
        NOW(), NOW()
    ),
    (
        'c3000001-0001-4000-8000-000000000004',
        'a1000001-0001-4000-8000-000000000004',
        'hanoi',
        88,
        'Giáº£ng CafÃ© lÃ  Ä‘á»‹a chá»‰ kinh Ä‘iá»ƒn cho cÃ  phÃª trá»©ng.',
        '{"sweet": 3, "salty": 0, "sour": 0, "spicy": 0, "rich": 4}'::jsonb,
        'CÃ  phÃª trá»©ng ra Ä‘á»i táº¡i HÃ  Ná»™i nhá»¯ng nÄƒm 1940.',
        NOW(), NOW()
    )
ON CONFLICT (food_id, city_slug) DO UPDATE SET
    score = EXCLUDED.score,
    local_description = EXCLUDED.local_description,
    flavor_profile = EXCLUDED.flavor_profile,
    fun_fact = EXCLUDED.fun_fact,
    updated_at = NOW();

-- -----------------------------------------------------------------------------
-- 3) QuÃ¡n test (places) â€” destinations pháº£i chá»©a "hanoi" (JSON array)
-- -----------------------------------------------------------------------------
INSERT INTO places (
    id, place_id, title, category, address, latitude, longitude,
    review_count, review_rating, adjusted_rating, thumbnail,
    place_group, destinations, created_at, updated_at
)
VALUES
    (
        'b2000001-0001-4000-8000-000000000001',
        'seed_hanoi_pho_thin',
        'Phá»Ÿ ThÃ¬n BÃ² HoÃ nh Ãnh',
        'Restaurant',
        '13 LÃ² ÄÃºc, Hai BÃ  TrÆ°ng, HÃ  Ná»™i',
        21.01780000, 105.84920000,
        1200, 4.3, 4.20,
        'https://images.unsplash.com/photo-1591814468924-caf88d1232e1?w=200',
        'FOOD_AND_DRINK',
        '["hanoi"]'::jsonb,
        NOW(), NOW()
    ),
    (
        'b2000001-0001-4000-8000-000000000002',
        'seed_hanoi_pho_suong',
        'Phá»Ÿ SÆ°á»›ng',
        'Restaurant',
        '24 Trung YÃªn, Cá»­a Nam, HoÃ n Kiáº¿m, HÃ  Ná»™i',
        21.02850000, 105.84200000,
        800, 4.1, 4.00,
        'https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?w=200',
        'FOOD_AND_DRINK',
        '["hanoi"]'::jsonb,
        NOW(), NOW()
    ),
    (
        'b2000001-0001-4000-8000-000000000003',
        'seed_hanoi_buncha_huonglien',
        'BÃºn cháº£ HÆ°Æ¡ng LiÃªn',
        'Restaurant',
        '24 LÃª VÄƒn HÆ°u, Hai BÃ  TrÆ°ng, HÃ  Ná»™i',
        21.01560000, 105.85140000,
        3500, 4.2, 4.10,
        'https://images.unsplash.com/photo-1559339352-11d035aa65de?w=200',
        'FOOD_AND_DRINK',
        '["hanoi"]'::jsonb,
        NOW(), NOW()
    ),
    (
        'b2000001-0001-4000-8000-000000000004',
        'seed_hanoi_buncha_dac_kim',
        'BÃºn cháº£ Äáº¯c Kim',
        'Restaurant',
        '1 HÃ ng MÃ nh, HoÃ n Kiáº¿m, HÃ  Ná»™i',
        21.03120000, 105.84850000,
        2100, 4.0, 3.90,
        'https://images.unsplash.com/photo-1559339352-11d035aa65de?w=200',
        'FOOD_AND_DRINK',
        '["hanoi"]'::jsonb,
        NOW(), NOW()
    ),
    (
        'b2000001-0001-4000-8000-000000000005',
        'seed_hanoi_banhcuon_thanh_tri',
        'BÃ¡nh cuá»‘n Thanh TrÃ¬ (phá»‘ cá»•)',
        'Restaurant',
        '66 BÃ¡t ÄÃ n, HoÃ n Kiáº¿m, HÃ  Ná»™i',
        21.03480000, 105.84720000,
        450, 4.4, 4.30,
        'https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=200',
        'FOOD_AND_DRINK',
        '["hanoi"]'::jsonb,
        NOW(), NOW()
    ),
    (
        'b2000001-0001-4000-8000-000000000006',
        'seed_hanoi_ca_phe_trung_giang',
        'CÃ  phÃª Giáº£ng',
        'Cafe',
        '39 Nguyá»…n Há»¯u HuÃ¢n, HoÃ n Kiáº¿m, HÃ  Ná»™i',
        21.03350000, 105.85210000,
        5200, 4.5, 4.40,
        'https://images.unsplash.com/photo-1514434753793-5c31e2a088e6?w=200',
        'FOOD_AND_DRINK',
        '["hanoi"]'::jsonb,
        NOW(), NOW()
    ),
    (
        'b2000001-0001-4000-8000-000000000007',
        'seed_hanoi_ca_phe_dinh',
        'CÃ  phÃª Äinh',
        'Cafe',
        '13 Äinh TiÃªn HoÃ ng, HoÃ n Kiáº¿m, HÃ  Ná»™i',
        21.02890000, 105.85280000,
        1800, 4.3, 4.15,
        'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=200',
        'FOOD_AND_DRINK',
        '["hanoi"]'::jsonb,
        NOW(), NOW()
    )
ON CONFLICT (place_id) DO UPDATE SET
    title = EXCLUDED.title,
    address = EXCLUDED.address,
    latitude = EXCLUDED.latitude,
    longitude = EXCLUDED.longitude,
    adjusted_rating = EXCLUDED.adjusted_rating,
    thumbnail = EXCLUDED.thumbnail,
    place_group = EXCLUDED.place_group,
    destinations = EXCLUDED.destinations,
    updated_at = NOW();

-- -----------------------------------------------------------------------------
-- 4) LiÃªn káº¿t quÃ¡n â†” mÃ³n (place_foods)
-- -----------------------------------------------------------------------------
INSERT INTO place_foods (place_id, food_id) VALUES
    ('b2000001-0001-4000-8000-000000000001', 'a1000001-0001-4000-8000-000000000001'),
    ('b2000001-0001-4000-8000-000000000002', 'a1000001-0001-4000-8000-000000000001'),
    ('b2000001-0001-4000-8000-000000000003', 'a1000001-0001-4000-8000-000000000002'),
    ('b2000001-0001-4000-8000-000000000004', 'a1000001-0001-4000-8000-000000000002'),
    ('b2000001-0001-4000-8000-000000000005', 'a1000001-0001-4000-8000-000000000003'),
    ('b2000001-0001-4000-8000-000000000006', 'a1000001-0001-4000-8000-000000000004'),
    ('b2000001-0001-4000-8000-000000000007', 'a1000001-0001-4000-8000-000000000004')
ON CONFLICT DO NOTHING;

COMMIT;

-- -----------------------------------------------------------------------------
-- Kiá»ƒm tra nhanh
-- -----------------------------------------------------------------------------
SELECT f.name_vi, fcs.score, fcs.city_slug
FROM foods f
JOIN food_city_scores fcs ON fcs.food_id = f.id
WHERE fcs.city_slug = 'hanoi'
ORDER BY fcs.score DESC;

SELECT f.name_vi AS mon, p.title AS quan
FROM place_foods pf
JOIN foods f ON f.id = pf.food_id
JOIN places p ON p.id = pf.place_id
WHERE p.destinations @> '["hanoi"]'::jsonb
ORDER BY f.name_vi, p.title;
