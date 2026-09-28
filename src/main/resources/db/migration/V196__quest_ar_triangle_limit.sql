-- AR objects (§3.15): the most triangles a GLB may have, as a row an admin can change on the
-- console's config page (label QUEST). The code falls back to the same default when it is missing.
-- ON CONFLICT DO NOTHING: a value an admin already set is never overwritten.
INSERT INTO config (label, key, value, description, is_active)
VALUES
    ('QUEST', 'AR_MAX_TRIANGLES', '1000000',
     'So tam giac toi da cua file GLB vat the AR (canh bao tu 150000: may tam trung co the giat)', TRUE)
ON CONFLICT (label, key) DO NOTHING;
