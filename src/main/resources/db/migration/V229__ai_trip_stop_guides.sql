-- AI trip planner: each stop now carries a time-to-spend estimate and a short practical guide
-- (what to do or order, tips, cautions) instead of a single sentence. See
-- python_ai_trip_service/app/prompts.py (DEFAULT_PLAN_RULES rule 10, STOP_WRITING_RULES).
--
-- Both updates touch only what is still the V148 seed: an operator's own edit of the rule text
-- or of the length is kept as it is.

UPDATE config
SET value = replace(value,
        '10. Descriptions: ONE concise sentence per stop (why go + what to do/eat there), grounded in the given data (a menu dish, the view, the vibe). Trip description: at most 2 short sentences. No marketing fluff, no invented facts.',
        '10. Descriptions: a short practical guide per stop, written for this traveller (see "Writing each stop" below), grounded in the given data. Trip description: at most 2 short sentences. No marketing fluff, no invented facts.')
WHERE label = 'AI_TRIP' AND key = 'PLAN_RULES';

UPDATE config
SET value = '600',
    description = 'Maximum characters for each activity guide written by the AI (trips longer than 7 days use less automatically).'
WHERE label = 'AI_TRIP' AND key = 'DESCRIPTION_MAX_CHARS' AND trim(value) = '200';
