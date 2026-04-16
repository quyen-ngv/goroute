-- Make sort_order nullable since we now sort by start_time
ALTER TABLE activities ALTER COLUMN sort_order DROP NOT NULL;
