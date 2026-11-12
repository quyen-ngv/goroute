-- Managed accounts (epic 20, .docs/BLOG_POSTS_PLAN_2026-11-10.md §2): ordinary HUMAN user
-- accounts an admin holds, posts check-ins and articles for, and later hands over to the real
-- person. account_type stays HUMAN on purpose -- the account shows in search, Discover and
-- follower lists like anyone else -- and only the top-active ranking leaves it out.
--
-- While managed_by_admin is TRUE the console may act as the account; the handover sets it to FALSE
-- for good and records when and by whom.

ALTER TABLE users ADD COLUMN IF NOT EXISTS managed_by_admin BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS managed_since TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS handed_over_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS handed_over_by UUID;

-- The console list, newest first. Partial: managed accounts are a handful among all users.
CREATE INDEX IF NOT EXISTS idx_users_managed ON users (managed_since DESC) WHERE managed_by_admin = TRUE;
