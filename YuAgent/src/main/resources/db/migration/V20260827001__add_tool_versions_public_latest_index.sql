CREATE INDEX IF NOT EXISTS idx_tool_versions_public_latest
    ON tool_versions (tool_id, created_at DESC, id DESC)
    WHERE public_status = TRUE AND deleted_at IS NULL;
