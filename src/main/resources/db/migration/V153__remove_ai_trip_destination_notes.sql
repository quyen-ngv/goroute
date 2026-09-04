-- DESTINATION_NOTES contained a fixed city list and is no longer part of AI trip prompt configuration.
DELETE FROM config
WHERE label = 'AI_TRIP'
  AND key = 'DESTINATION_NOTES';
