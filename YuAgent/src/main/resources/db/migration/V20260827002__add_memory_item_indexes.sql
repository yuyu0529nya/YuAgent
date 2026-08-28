CREATE INDEX IF NOT EXISTS idx_memory_items_user_dedupe_hash
    ON memory_items (user_id, dedupe_hash)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_memory_items_user_updated_at
    ON memory_items (user_id, updated_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_memory_items_user_type_updated_at
    ON memory_items (user_id, type, updated_at DESC)
    WHERE deleted_at IS NULL;
