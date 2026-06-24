-- V16: Drop legacy role column from app_user
-- Handles the case where V2 didn't effectively drop it.

DO $$
BEGIN
    BEGIN
        ALTER TABLE app_user DROP COLUMN role;
    EXCEPTION
        WHEN undefined_column THEN
            -- Column already gone, nothing to do
            NULL;
    END;
END $$;
