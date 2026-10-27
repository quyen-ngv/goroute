-- The app is now called Pengoo. V149 seeded the Pro plan names with the old brand,
-- and the app shows display_name on the subscription screens.
UPDATE subscription_plans
SET display_name = REPLACE(display_name, 'Vietdetour', 'Pengoo'),
    updated_at = NOW()
WHERE display_name LIKE '%Vietdetour%';
