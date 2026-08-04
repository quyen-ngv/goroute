-- Keep values written by the previous catalog version compatible with the
-- documented schema v1 key.
UPDATE places
SET attributes = (attributes - 'late_night')
    || jsonb_build_object('late_night_available', attributes -> 'late_night')
WHERE attributes ? 'late_night'
  AND NOT attributes ? 'late_night_available';

UPDATE places
SET attributes = attributes - 'late_night'
WHERE attributes ? 'late_night';
