package org.yu.infrastructure.initializer;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Adds the indexes required by the user RAG snapshot read and cleanup paths.
 *
 * <p>
 * Existing installations can retain database volumes that predate these indexes, so this compatibility check is
 * intentionally idempotent. */
@Component
@Order(28)
public class UserRagSnapshotSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserRagSnapshotSchemaInitializer.class);

    private static final List<String> INDEX_PATCHES = List.of(
            "CREATE INDEX IF NOT EXISTS idx_user_rag_files_user_rag_created_at "
                    + "ON public.user_rag_files (user_rag_id, created_at DESC)",
            "CREATE INDEX IF NOT EXISTS idx_user_rag_files_user_rag_original_file "
                    + "ON public.user_rag_files (user_rag_id, original_file_id)",
            "CREATE INDEX IF NOT EXISTS idx_user_rag_documents_user_rag_created_at "
                    + "ON public.user_rag_documents (user_rag_id, created_at DESC)",
            "CREATE INDEX IF NOT EXISTS idx_user_rag_documents_user_rag_file_created_at "
                    + "ON public.user_rag_documents (user_rag_id, user_rag_file_id, created_at DESC)",
            "CREATE INDEX IF NOT EXISTS idx_user_rag_documents_file_page "
                    + "ON public.user_rag_documents (user_rag_file_id, page DESC)");

    private final JdbcTemplate jdbcTemplate;

    public UserRagSnapshotSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            for (String sql : INDEX_PATCHES) {
                jdbcTemplate.execute(sql);
            }
            log.info("user RAG snapshot schema compatibility check completed");
        } catch (Exception e) {
            log.error("Failed to ensure user RAG snapshot schema compatibility", e);
        }
    }
}
