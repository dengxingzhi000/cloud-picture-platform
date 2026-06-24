-- V33: Add soft delete support to picture_asset
ALTER TABLE picture_asset ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE picture_asset ADD COLUMN deleted_by UUID;

CREATE INDEX idx_picture_deleted_at ON picture_asset(deleted_at) WHERE deleted_at IS NOT NULL;
