-- Add share configuration fields to trips table
ALTER TABLE trips ADD COLUMN share_expenses BOOLEAN DEFAULT FALSE;
ALTER TABLE trips ADD COLUMN share_notes BOOLEAN DEFAULT FALSE;
