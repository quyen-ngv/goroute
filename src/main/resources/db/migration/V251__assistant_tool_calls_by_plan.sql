-- Pengoo's tool-call budget per question now depends on the asker's plan: a FREE account keeps the
-- 6 calls every question had (AssistantContextBuilder.MAX_TOOL_CALLS), a PRO account gets 20 for a
-- deeper search. Each call of a parallel round counts; web search (1) and page reads (2) stay
-- capped inside the budget by the worker.
--
-- A 20-call answer outlasts the old 150-second turn timeout (the worker itself stopped at 120 s),
-- so the timeout goes to 300 s where it is still the seeded 150; the worker now stops 15 s before it.

INSERT INTO config (label, key, value, description, is_active) VALUES
    ('ASSISTANT', 'FREE_MAX_TOOL_CALLS', '6', 'So lan goi cong cu Pengoo duoc dung cho mot cau hoi cua tai khoan FREE (1-30)', TRUE),
    ('ASSISTANT', 'PRO_MAX_TOOL_CALLS', '20', 'So lan goi cong cu Pengoo duoc dung cho mot cau hoi cua tai khoan PRO (1-50)', TRUE)
ON CONFLICT DO NOTHING;

UPDATE config
SET value = '300'
WHERE label = 'ASSISTANT' AND key = 'TURN_TIMEOUT_SECONDS' AND value = '150';
