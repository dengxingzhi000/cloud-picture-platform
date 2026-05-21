-- V15: Drop legacy role column from app_user if it still exists
-- The V2 migration was supposed to drop this, but it may have failed.
-- This migration is idempotent — safe to run even if the column is already gone.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'app_user' AND column_name = 'role'
    ) THEN
        ALTER TABLE app_user DROP COLUMN role;
    END IF;
END $$;
