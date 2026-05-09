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
 * Ensures the agent_widgets schema exists for environments that reuse an older
 * database volume and skipped later schema changes.
 */
@Component
@Order(25)
public class AgentWidgetSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentWidgetSchemaInitializer.class);

    private static final List<String> SCHEMA_PATCHES = List.of(
            """
            CREATE TABLE IF NOT EXISTS public.agent_widgets (
                id character varying(32) NOT NULL,
                agent_id character varying(32) NOT NULL,
                user_id character varying(32) NOT NULL,
                public_id character varying(32) NOT NULL,
                name character varying(100) NOT NULL,
                description text,
                model_id character varying(32) NOT NULL,
                provider_id character varying(32),
                allowed_domains text,
                daily_limit integer DEFAULT -1,
                enabled boolean DEFAULT true,
                created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
                updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
                deleted_at timestamp without time zone,
                widget_type character varying(20) NOT NULL DEFAULT 'AGENT',
                knowledge_base_ids jsonb
            )
            """,
            "ALTER TABLE public.agent_widgets ADD COLUMN IF NOT EXISTS provider_id character varying(32)",
            "ALTER TABLE public.agent_widgets ADD COLUMN IF NOT EXISTS allowed_domains text",
            "ALTER TABLE public.agent_widgets ADD COLUMN IF NOT EXISTS daily_limit integer DEFAULT -1",
            "ALTER TABLE public.agent_widgets ADD COLUMN IF NOT EXISTS enabled boolean DEFAULT true",
            "ALTER TABLE public.agent_widgets ADD COLUMN IF NOT EXISTS created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP",
            "ALTER TABLE public.agent_widgets ADD COLUMN IF NOT EXISTS updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP",
            "ALTER TABLE public.agent_widgets ADD COLUMN IF NOT EXISTS deleted_at timestamp without time zone",
            "ALTER TABLE public.agent_widgets ADD COLUMN IF NOT EXISTS widget_type character varying(20) NOT NULL DEFAULT 'AGENT'",
            "ALTER TABLE public.agent_widgets ADD COLUMN IF NOT EXISTS knowledge_base_ids jsonb",
            """
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1
                    FROM pg_constraint
                    WHERE conname = 'agent_widgets_pkey'
                      AND conrelid = 'public.agent_widgets'::regclass
                ) THEN
                    ALTER TABLE public.agent_widgets ADD CONSTRAINT agent_widgets_pkey PRIMARY KEY (id);
                END IF;
            END
            $$;
            """,
            "CREATE UNIQUE INDEX IF NOT EXISTS agent_embeds_public_id_key ON public.agent_widgets USING btree (public_id)",
            "CREATE INDEX IF NOT EXISTS idx_agent_embeds_agent_id ON public.agent_widgets USING btree (agent_id)",
            "CREATE INDEX IF NOT EXISTS idx_agent_embeds_user_id ON public.agent_widgets USING btree (user_id)",
            "CREATE INDEX IF NOT EXISTS idx_agent_embeds_public_id ON public.agent_widgets USING btree (public_id)",
            "CREATE INDEX IF NOT EXISTS idx_agent_embeds_enabled ON public.agent_widgets USING btree (enabled)");

    private final JdbcTemplate jdbcTemplate;

    public AgentWidgetSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            for (String sql : SCHEMA_PATCHES) {
                jdbcTemplate.execute(sql);
            }
            log.info("agent_widgets schema compatibility check completed");
        } catch (Exception e) {
            log.error("Failed to ensure agent_widgets schema compatibility", e);
        }
    }
}
