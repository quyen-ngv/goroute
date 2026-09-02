-- Points are halved once a year instead of expiring by age.
--
-- V123 seeded POINTS.EXPIRY_DAYS with a default of 0 ("never"), and nothing ever read it: there
-- was no job, so the key promised an expiry the product did not have. The rule as decided is a
-- yearly halving, which these two keys describe and StarYearEndResetJob carries out.

INSERT INTO config (label, key, value, description, is_active) VALUES
    ('POINTS', 'YEAR_END_RESET_ENABLED', 'true',
     'Bat viec reset diem cuoi nam. Tat thi so du giu nguyen sang nam sau.', TRUE),
    ('POINTS', 'YEAR_END_RETAIN_PERCENT', '50',
     'Phan tram diem giu lai khi sang nam moi (0..100). 50 la con mot nua; 100 tuong duong tat.', TRUE)
ON CONFLICT (label, key) DO NOTHING;

-- Superseded, not deleted: the row is still in the console and an operator who finds it deserves
-- to be told it does nothing rather than to change it and wait for an effect that never comes.
UPDATE config
SET description = 'KHONG CON DUNG. Diem khong het han theo ngay; xem POINTS.YEAR_END_RETAIN_PERCENT.',
    is_active = FALSE
WHERE label = 'POINTS' AND key = 'EXPIRY_DAYS';
