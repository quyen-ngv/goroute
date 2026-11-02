-- The app is called OnDetour again; Pengoo stays as the chat assistant's name only.
-- V257 renamed the Pro plan names to Pengoo, and the app shows display_name on the
-- subscription screens.
UPDATE subscription_plans
SET display_name = REPLACE(display_name, 'Pengoo', 'OnDetour'),
    updated_at = NOW()
WHERE display_name LIKE '%Pengoo%';
