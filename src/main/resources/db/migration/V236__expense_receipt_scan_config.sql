-- Receipt scan (epic 05): a photo of a bill fills the add-expense form through a vision model.
-- Off for everyone except USER.BETA_USER until RECEIPT_SCAN_ENABLED is switched on.
-- The daily ledger is ai_api_calls (feature RECEIPT_SCAN), so no new table.

INSERT INTO config (label, key, value, description, is_active)
VALUES ('EXPENSE', 'RECEIPT_SCAN_ENABLED', 'false',
        'Bat quet hoa don cho moi nguoi; khi tat chi USER.BETA_USER dung duoc', TRUE),
       ('EXPENSE', 'RECEIPT_SCAN_FREE_DAILY', '10',
        'So lan quet hoa don moi ngay cho tai khoan FREE (0-1000)', TRUE),
       ('EXPENSE', 'RECEIPT_SCAN_PRO_DAILY', '50',
        'So lan quet hoa don moi ngay cho tai khoan PRO (0-10000)', TRUE)
ON CONFLICT (label, key) DO NOTHING;
