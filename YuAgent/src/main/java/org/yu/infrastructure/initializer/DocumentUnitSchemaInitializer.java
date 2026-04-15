package org.yu.infrastructure.initializer;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures newer document_unit metadata columns exist even when DB migrations
 * were skipped in an existing environment.
 */
@Component
@Order(20)
public class DocumentUnitSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DocumentUnitSchemaInitializer.class);

    private static final List<String> SCHEMA_PATCHES = List.of(
            "ALTER TABLE public.document_unit ADD COLUMN IF NOT EXISTS title_path VARCHAR(1000)",
            "ALTER TABLE public.document_unit ADD COLUMN IF NOT EXISTS segment_type VARCHAR(32)",
            "ALTER TABLE public.document_unit ADD COLUMN IF NOT EXISTS segment_order INTEGER",
            "ALTER TABLE public.document_unit ADD COLUMN IF NOT EXISTS source_page INTEGER",
            "ALTER TABLE public.document_unit ADD COLUMN IF NOT EXISTS metadata_json TEXT",
            "CREATE INDEX IF NOT EXISTS idx_document_unit_file_source_page ON public.document_unit (file_id, source_page)",
            "CREATE INDEX IF NOT EXISTS idx_document_unit_file_segment_order ON public.document_unit (file_id, segment_order)");

    private final JdbcTemplate jdbcTemplate;

    public DocumentUnitSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            for (String sql : SCHEMA_PATCHES) {
                jdbcTemplate.execute(sql);
            }
            log.info("document_unit schema compatibility check completed");
        } catch (Exception e) {
            log.error("Failed to ensure document_unit schema compatibility", e);
        }
    }
}
