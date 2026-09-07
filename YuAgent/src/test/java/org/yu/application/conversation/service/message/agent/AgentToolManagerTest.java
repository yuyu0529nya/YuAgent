package org.yu.application.conversation.service.message.agent;

import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentToolManagerTest {

    @Test
    void shouldCloseEachMcpClientOnlyOnce() throws Exception {
        McpClient firstClient = mock(McpClient.class);
        McpClient secondClient = mock(McpClient.class);
        ToolProvider delegate = mock(ToolProvider.class);
        AgentToolManager.CloseableToolProvider provider = new AgentToolManager.CloseableToolProvider(delegate,
                List.of(firstClient, secondClient));

        provider.close();
        provider.close();

        verify(firstClient).close();
        verify(secondClient).close();
    }

    @Test
    void shouldMergePresetsWhenServersShareOneEndpoint() {
        Map<String, Map<String, Map<String, String>>> presets = new LinkedHashMap<>();
        presets.put("weather", Map.of("forecast", Map.of("city", "Shanghai")));
        presets.put("maps", Map.of("route", Map.of("mode", "walking")));

        Map<String, String> merged = AgentToolManager.mergePresetArguments(List.of("weather", "maps"), presets);

        assertEquals("{\"city\":\"Shanghai\"}", merged.get("weather_forecast"));
        assertEquals("{\"mode\":\"walking\"}", merged.get("maps_route"));
        assertEquals("{\"city\":\"Shanghai\"}", merged.get("forecast"));
        assertEquals("{\"mode\":\"walking\"}", merged.get("route"));
    }

    @Test
    void shouldKeepNamespacedPresetsWhenRawToolNamesCollide() {
        Map<String, Map<String, Map<String, String>>> presets = new LinkedHashMap<>();
        presets.put("first", Map.of("lookup", Map.of("city", "Shanghai")));
        presets.put("second", Map.of("lookup", Map.of("city", "Beijing")));

        Map<String, String> merged = AgentToolManager.mergePresetArguments(List.of("first", "second"), presets);

        assertEquals("{\"city\":\"Shanghai\"}", merged.get("first_lookup"));
        assertEquals("{\"city\":\"Beijing\"}", merged.get("second_lookup"));
        assertFalse(merged.containsKey("lookup"));
    }
}
