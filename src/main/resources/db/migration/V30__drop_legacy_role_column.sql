-- V15: Drop legacy role column from app_user
-- The V2 migration was supposed to drop this, but it may have failed.
-- First remove NOT NULL constraint, then drop the column.

ALTER TABLE app_user ALTER COLUMN role DROP NOT NULL;
ALTER TABLE app_user DROP COLUMN role;
