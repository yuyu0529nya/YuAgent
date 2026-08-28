package org.yu.infrastructure.initializer;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Ensures the indexes that back the application's most frequent user-scoped list and ownership queries are present on
 * upgraded database volumes. */
@Component
@Order(29)
public class CoreQueryIndexSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CoreQueryIndexSchemaInitializer.class);

    private static final List<String> INDEX_PATCHES = List.of(
            "CREATE INDEX IF NOT EXISTS idx_agent_workspace_user_agent_active "
                    + "ON public.agent_workspace (user_id, agent_id) WHERE deleted_at IS NULL",
            "CREATE INDEX IF NOT EXISTS idx_sessions_agent_user_created_active "
                    + "ON public.sessions (agent_id, user_id, created_at DESC) WHERE deleted_at IS NULL",
            "CREATE INDEX IF NOT EXISTS idx_sessions_user_updated_active "
                    + "ON public.sessions (user_id, updated_at DESC) WHERE deleted_at IS NULL",
            "CREATE INDEX IF NOT EXISTS idx_user_tools_user_tool_active "
                    + "ON public.user_tools (user_id, tool_id) WHERE deleted_at IS NULL",
            "CREATE INDEX IF NOT EXISTS idx_user_tools_tool_active "
                    + "ON public.user_tools (tool_id) WHERE deleted_at IS NULL",
            "CREATE INDEX IF NOT EXISTS idx_user_tools_user_server_active "
                    + "ON public.user_tools (user_id, mcp_server_name) WHERE deleted_at IS NULL",
            "CREATE INDEX IF NOT EXISTS idx_tool_versions_tool_version_active "
                    + "ON public.tool_versions (tool_id, version) WHERE deleted_at IS NULL",
            "CREATE INDEX IF NOT EXISTS idx_tool_versions_tool_created_active "
                    + "ON public.tool_versions (tool_id, created_at DESC) WHERE deleted_at IS NULL",
            "CREATE INDEX IF NOT EXISTS idx_user_rags_user_installed_active "
                    + "ON public.user_rags (user_id, installed_at DESC) WHERE deleted_at IS NULL",
            "CREATE INDEX IF NOT EXISTS idx_user_rags_user_version_active "
                    + "ON public.user_rags (user_id, rag_version_id) WHERE deleted_at IS NULL",
            "CREATE INDEX IF NOT EXISTS idx_user_rags_user_original_active "
                    + "ON public.user_rags (user_id, original_rag_id) WHERE deleted_at IS NULL");

    private final JdbcTemplate jdbcTemplate;

    public CoreQueryIndexSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            for (String sql : INDEX_PATCHES) {
                jdbcTemplate.execute(sql);
            }
            log.info("core query index compatibility check completed");
        } catch (Exception e) {
            log.error("Failed to ensure core query indexes", e);
        }
    }
}
