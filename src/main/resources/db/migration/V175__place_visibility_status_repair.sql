-- places.visibility_status has been a free VARCHAR since V069, and PartnerPlaceMapper's
-- insertCanonical wrote the literal 'PUBLIC' into it. PlaceVisibilityStatus only knows
-- ACTIVE and INACTIVE, so MyBatis threw on *every* read of such a row -- which is why
-- finishing the stay wizard failed at submit: partnerCreateHotel reads back the place it
-- just created.
--
-- Repair the rows, then stop the column from accepting anything else. The constraint is
-- the point: without it the next stray literal is found by a 500 in production rather
-- than by a failed insert.

-- 'PUBLIC' was written to mean "visible", which is what ACTIVE means here.
UPDATE places SET visibility_status = 'ACTIVE' WHERE visibility_status = 'PUBLIC';

-- The synonyms on the other side, in case any predate V069's default.
UPDATE places SET visibility_status = 'INACTIVE'
WHERE visibility_status IN ('PRIVATE', 'HIDDEN', 'DRAFT');

-- Anything still unrecognised is already invisible everywhere -- reading it throws --
-- so calling it INACTIVE loses nothing and makes the row editable in the console again.
-- The alternative, guessing ACTIVE, would publish a place nobody has looked at.
UPDATE places SET visibility_status = 'INACTIVE'
WHERE visibility_status IS NULL OR visibility_status NOT IN ('ACTIVE', 'INACTIVE');

ALTER TABLE places
    DROP CONSTRAINT IF EXISTS ck_places_visibility_status;

ALTER TABLE places
    ADD CONSTRAINT ck_places_visibility_status
    CHECK (visibility_status IN ('ACTIVE', 'INACTIVE'));
