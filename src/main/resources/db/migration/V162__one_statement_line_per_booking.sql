-- One statement line per booking, across statements — not just within one.
--
-- V144 made the line unique per (statement_id, booking), which stops a regeneration from
-- duplicating a stay inside its own statement but says nothing about a second statement.
-- adminGenerate accepts any period, so 01..15 August followed by the ordinary 01..31 August run
-- billed the same stays twice. The generator now leaves out bookings another statement already
-- carries; this index is the guarantee behind that query.
--
-- Every statement counts: a period is never voided, it is regenerated (undisputed lines are
-- deleted and rebuilt in the same transaction, which this index allows) or settled.
--
-- Existing data is checked first. A database that already bills a booking twice is a billing
-- question, not a schema question: the migration reports it and leaves the index off rather
-- than failing every deployment until somebody guesses which line to delete.

DO $$
DECLARE
    duplicated_hotel BIGINT;
    duplicated_activity BIGINT;
BEGIN
    SELECT COUNT(*) INTO duplicated_hotel FROM (
        SELECT hotel_booking_id FROM partner_statement_lines
        WHERE hotel_booking_id IS NOT NULL
        GROUP BY hotel_booking_id HAVING COUNT(*) > 1) AS d;

    SELECT COUNT(*) INTO duplicated_activity FROM (
        SELECT activity_order_id FROM partner_statement_lines
        WHERE activity_order_id IS NOT NULL
        GROUP BY activity_order_id HAVING COUNT(*) > 1) AS d;

    IF duplicated_hotel > 0 OR duplicated_activity > 0 THEN
        RAISE WARNING 'partner_statement_lines already bills % hotel booking(s) and % activity order(s) on more than one statement. The unique index was NOT created. Reconcile those bookings (keep one line each) and re-run: CREATE UNIQUE INDEX uq_statement_line_hotel_booking ON partner_statement_lines(hotel_booking_id) WHERE hotel_booking_id IS NOT NULL; CREATE UNIQUE INDEX uq_statement_line_activity_order ON partner_statement_lines(activity_order_id) WHERE activity_order_id IS NOT NULL;',
            duplicated_hotel, duplicated_activity;
    ELSE
        CREATE UNIQUE INDEX IF NOT EXISTS uq_statement_line_hotel_booking
            ON partner_statement_lines(hotel_booking_id) WHERE hotel_booking_id IS NOT NULL;
        CREATE UNIQUE INDEX IF NOT EXISTS uq_statement_line_activity_order
            ON partner_statement_lines(activity_order_id) WHERE activity_order_id IS NOT NULL;
    END IF;
END $$;
