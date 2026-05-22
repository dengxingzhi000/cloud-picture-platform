CREATE TABLE excalidraw_scene (
    id UUID PRIMARY KEY,
    picture_id UUID REFERENCES picture(id),
    scene_name VARCHAR(255) NOT NULL,
    snapshot_data TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    last_seq BIGINT NOT NULL DEFAULT 0,
    last_updated_by_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_excalidraw_scene_picture_id ON excalidraw_scene(picture_id);
CREATE INDEX idx_excalidraw_scene_updated_at ON excalidraw_scene(updated_at);

CREATE TABLE excalidraw_file (
    id UUID PRIMARY KEY,
    scene_id UUID NOT NULL REFERENCES excalidraw_scene(id) ON DELETE CASCADE,
    file_id VARCHAR(255) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    url VARCHAR(1024) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(scene_id, file_id)
);

CREATE INDEX idx_excalidraw_file_scene_id ON excalidraw_file(scene_id);
