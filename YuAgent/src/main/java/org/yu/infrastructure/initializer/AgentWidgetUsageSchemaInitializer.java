package org.yu.infrastructure.initializer;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(27)
public class AgentWidgetUsageSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentWidgetUsageSchemaInitializer.class);

    private static final List<String> SCHEMA_PATCHES = List.of("""
            CREATE TABLE IF NOT EXISTS public.agent_widget_daily_usage (
                widget_id character varying(32) NOT NULL,
                usage_date date NOT NULL,
                call_count integer NOT NULL DEFAULT 0,
                created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
                updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT agent_widget_daily_usage_pkey PRIMARY KEY (widget_id, usage_date)
            )
            """,
            "ALTER TABLE public.agent_widget_daily_usage ADD COLUMN IF NOT EXISTS call_count integer NOT NULL DEFAULT 0",
            "ALTER TABLE public.agent_widget_daily_usage ADD COLUMN IF NOT EXISTS created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP",
            "ALTER TABLE public.agent_widget_daily_usage ADD COLUMN IF NOT EXISTS updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP",
            "CREATE INDEX IF NOT EXISTS idx_agent_widget_daily_usage_date ON public.agent_widget_daily_usage (usage_date)",
            "CREATE INDEX IF NOT EXISTS idx_agent_widget_daily_usage_widget ON public.agent_widget_daily_usage (widget_id)");

    private final JdbcTemplate jdbcTemplate;

    public AgentWidgetUsageSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            for (String sql : SCHEMA_PATCHES) {
                jdbcTemplate.execute(sql);
            }
            log.info("agent_widget_daily_usage schema compatibility check completed");
        } catch (Exception e) {
            log.error("Failed to ensure agent_widget_daily_usage schema compatibility", e);
        }
    }
}
