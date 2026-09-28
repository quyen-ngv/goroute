-- Quest arrival (§3.8): one valid GPS sample inside the checkpoint unlocks it. Three samples five
-- seconds apart made players wait 15-30 s at every stop; the fix's accuracy is still checked, and
-- the sample is kept for the override-audit and health jobs. An admin can raise it again on the
-- console's config page (label QUEST).
INSERT INTO config (label, key, value, description, is_active)
VALUES
    ('QUEST', 'ARRIVAL_STABLE_SAMPLES', '1',
     'So mau GPS hop le lien tiep can de xac nhan da den checkpoint (1..10)', TRUE)
ON CONFLICT (label, key) DO UPDATE SET value = '1';
