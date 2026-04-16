-- Add photo_urls column to expenses table
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS photo_urls TEXT[];

-- Add comment
COMMENT ON COLUMN expenses.photo_urls IS 'Array of photo URLs for expense receipts';
