-- Admin queue "Activity chưa gắn địa điểm" (epic 02 §6): an operator can take an activity out
-- of the queue when there is nothing to link (a meal, "free time", a home address). Both columns
-- are admin-only; the app never reads them, and restoring the activity clears them again.

ALTER TABLE activities ADD COLUMN IF NOT EXISTS place_review_dismissed_at TIMESTAMP;
ALTER TABLE activities ADD COLUMN IF NOT EXISTS place_review_dismissed_by UUID;
