package org.yu.domain.agent.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AgentWidgetUsageDomainService {

    private static final String UPSERT_DAILY_CALL_SQL = """
            INSERT INTO public.agent_widget_daily_usage (widget_id, usage_date, call_count, created_at, updated_at)
            VALUES (?, CURRENT_DATE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (widget_id, usage_date)
            DO UPDATE SET call_count = public.agent_widget_daily_usage.call_count + 1,
                          updated_at = CURRENT_TIMESTAMP
            WHERE ? = -1 OR public.agent_widget_daily_usage.call_count < ?
            RETURNING call_count
            """;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public AgentWidgetUsageDomainService(JdbcTemplate jdbcTemplate,
            NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public int getTodayCallCount(String widgetId) {
        Integer count = jdbcTemplate.query(
                "SELECT call_count FROM public.agent_widget_daily_usage WHERE widget_id = ? AND usage_date = CURRENT_DATE",
                rs -> rs.next() ? rs.getInt("call_count") : 0,
                widgetId);
        return count != null ? count : 0;
    }

    public Map<String, Integer> getTodayCallCounts(List<String> widgetIds) {
        if (widgetIds == null || widgetIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Integer> usageMap = new HashMap<>();
        namedParameterJdbcTemplate.query(
                """
                        SELECT widget_id, call_count
                        FROM public.agent_widget_daily_usage
                        WHERE usage_date = CURRENT_DATE
                          AND widget_id IN (:widgetIds)
                        """,
                new MapSqlParameterSource("widgetIds", widgetIds),
                (rs, rowNum) -> {
                    usageMap.put(rs.getString("widget_id"), rs.getInt("call_count"));
                    return null;
                });
        return usageMap;
    }

    public Integer consumeDailyCall(String widgetId, Integer dailyLimit) {
        if (dailyLimit != null && dailyLimit == 0) {
            return null;
        }

        List<Integer> counts = jdbcTemplate.query(
                UPSERT_DAILY_CALL_SQL,
                (rs, rowNum) -> rs.getInt("call_count"),
                widgetId,
                dailyLimit != null ? dailyLimit : -1,
                dailyLimit != null ? dailyLimit : -1);

        return counts.isEmpty() ? null : counts.get(0);
    }
}
