INSERT INTO place_translations (place_id, locale, name, description, translation_source, created_at, updated_at)
SELECT p.id, 'vi', p.title, p.descriptions, 'LEGACY', p.created_at, p.updated_at
FROM places p
WHERE p.title IS NOT NULL
ON CONFLICT (place_id, locale) DO NOTHING;
