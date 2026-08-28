package org.yu.infrastructure.initializer;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Ensures trace detail schema stays compatible when an existing database volume missed later SQL updates. */
@Component
@Order(26)
public class AgentExecutionTraceSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutionTraceSchemaInitializer.class);

    private static final List<String> SCHEMA_PATCHES = List
            .of("ALTER TABLE IF EXISTS public.agent_execution_details ADD COLUMN IF NOT EXISTS fallback_reason TEXT");

    private final JdbcTemplate jdbcTemplate;

    public AgentExecutionTraceSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            for (String sql : SCHEMA_PATCHES) {
                jdbcTemplate.execute(sql);
            }
            log.info("agent_execution_details schema compatibility check completed");
        } catch (Exception e) {
            log.error("Failed to ensure agent_execution_details schema compatibility", e);
        }
    }
}
