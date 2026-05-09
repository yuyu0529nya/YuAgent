package org.yu.application.conversation.service.message.agent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolManagerTest {

    @Test
    void shouldReuseCachedResultForIdenticalToolRequest() throws Exception {
        AtomicInteger delegateCalls = new AtomicInteger(0);
        ToolExecutor delegate = (request, memoryId) -> {
            delegateCalls.incrementAndGet();
            return "sunny";
        };

        ToolExecutor guarded = createGuardedExecutor(delegate);
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("weather_forecast")
                .arguments("{\"city\":\"Shanghai\"}")
                .build();

        String firstResult = guarded.execute(request, null);
        String secondResult = guarded.execute(request, null);

        assertEquals("sunny", firstResult);
        assertEquals(1, delegateCalls.get());
        assertTrue(secondResult.contains("sunny"));
        assertTrue(secondResult.contains("系统提示"));
    }

    @Test
    void shouldBlockExtraWeatherCallsAfterSuccessfulLimit() throws Exception {
        AtomicInteger delegateCalls = new AtomicInteger(0);
        ToolExecutor delegate = (request, memoryId) -> {
            int callIndex = delegateCalls.incrementAndGet();
            return "weather-result-" + callIndex;
        };

        ToolExecutor guarded = createGuardedExecutor(delegate);

        String first = guarded.execute(weatherRequest("上海"), null);
        String second = guarded.execute(weatherRequest("外滩"), null);
        String third = guarded.execute(weatherRequest("迪士尼"), null);

        assertEquals("weather-result-1", first);
        assertEquals("weather-result-2", second);
        assertEquals(2, delegateCalls.get());
        assertTrue(third.contains("weather-result-2"));
        assertTrue(third.contains("天气数据"));
    }

    private ToolExecutor createGuardedExecutor(ToolExecutor delegate) throws Exception {
        AgentToolManager toolManager = new AgentToolManager(null);
        Method method = AgentToolManager.class.getDeclaredMethod(
                "wrapToolExecutor", ToolExecutor.class, Map.class, AtomicInteger.class, AtomicReference.class);
        method.setAccessible(true);
        return (ToolExecutor) method.invoke(
                toolManager,
                delegate,
                new ConcurrentHashMap<String, String>(),
                new AtomicInteger(0),
                new AtomicReference<String>(""));
    }

    private ToolExecutionRequest weatherRequest(String location) {
        return ToolExecutionRequest.builder()
                .name("景点名称天气预报")
                .arguments("{\"location\":\"" + location + "\"}")
                .build();
    }
}
