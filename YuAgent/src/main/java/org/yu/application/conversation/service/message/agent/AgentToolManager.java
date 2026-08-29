package org.yu.application.conversation.service.message.agent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yu.application.conversation.service.McpUrlProviderService;
import org.yu.application.conversation.service.handler.context.ChatContext;
import org.yu.infrastructure.utils.JsonUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class AgentToolManager {

    private static final Logger logger = LoggerFactory.getLogger(AgentToolManager.class);

    // Some hosted Streamable HTTP MCPs need a longer first handshake before
    // their tool list is available. Keep this aligned with the gateway's
    // downstream initialization window.
    private static final Duration MCP_TOOL_TIMEOUT = Duration.ofSeconds(45);
    private static final String REPEATED_TOOL_RESULT_HINT = "\n\n[系统提示] 这个工具的相同参数结果在本轮对话中已经返回过了。请直接基于上面的结果完成回答，不要继续重复调用同一个工具。";
    private static final int MAX_SUCCESSFUL_WEATHER_TOOL_CALLS_PER_CHAT = 2;
    private static final String WEATHER_TOOL_LIMIT_HINT = "\n\n[系统提示] 本轮对话已经获取到足够的天气数据。对于同一城市或同一趟旅行行程，不要继续按景点逐个查询天气，请直接基于已有天气结果给出完整回答。";

    private static final Set<String> LIGHTWEIGHT_MESSAGES = Set.of("你好", "您好", "hi", "hello", "hey", "在吗", "在么", "嗨",
            "hello?");

    private static final Set<String> WEATHER_HINTS = Set.of("天气", "气温", "温度", "下雨", "降雨", "预报", "风力", "湿度", "空气质量",
            "紫外线", "实时天气", "未来天气", "今日天气", "明天天气", "后天天气", "穿衣", "出行", "旅游", "行程", "沈阳", "北京", "上海", "广州", "深圳", "杭州",
            "南京", "青岛", "成都", "重庆");

    private static final Set<String> MAP_HINTS = Set.of("高德", "地图", "路线", "导航", "怎么去", "如何去", "驾车", "开车", "步行", "骑行",
            "公交", "地铁", "怎么走", "交通", "公共交通", "换乘", "坐车", "附近", "周边", "地点", "位置", "经纬度", "距离", "路径", "景点", "出发", "起点",
            "终点");

    private static final Set<String> SURGE_HINTS = Set.of("surge", "部署", "发布", "上线", "login", "登录", "domain", "域名");

    private final McpUrlProviderService mcpUrlProviderService;

    public AgentToolManager(McpUrlProviderService mcpUrlProviderService) {
        this.mcpUrlProviderService = mcpUrlProviderService;
    }

    public ToolProvider createToolProvider(List<String> mcpServerNames,
            Map<String, Map<String, Map<String, String>>> toolPresetParams, String userId) {
        if (mcpServerNames == null || mcpServerNames.isEmpty()) {
            return null;
        }

        List<McpClient> mcpClients = new ArrayList<>();
        Map<String, String> resolvedToolUrls = new LinkedHashMap<>();

        // The hosted services share a single gateway session. Deploy every
        // requested service before creating that session; otherwise only the
        // first service is present when the gateway loads its tool list.
        for (String mcpServerName : mcpServerNames) {
            try {
                resolvedToolUrls.put(mcpServerName, mcpUrlProviderService.getMcpToolUrl(mcpServerName, userId));
            } catch (Exception e) {
                logger.warn("Skipping unavailable MCP tool {}: {}", mcpServerName, e.getMessage());
            }
        }

        Set<String> connectedUrls = new HashSet<>();
        for (Map.Entry<String, String> toolUrl : resolvedToolUrls.entrySet()) {
            String mcpServerName = toolUrl.getKey();
            try {
                String mcpUrl = toolUrl.getValue();
                // Hosted MCP tools share one gateway SSE endpoint. Connecting
                // once avoids registering the gateway's aggregated tools more
                // than once (which LangChain rejects as duplicate definitions).
                if (!connectedUrls.add(mcpUrl)) {
                    logger.debug("Skipping duplicate hosted MCP endpoint for {}", mcpServerName);
                    continue;
                }
                McpTransport transport = StreamableHttpMcpTransport.builder().url(mcpUrl).logRequests(false)
                        .logResponses(false).timeout(MCP_TOOL_TIMEOUT).build();

                McpClient mcpClient = new DefaultMcpClient.Builder().transport(transport).build();
                if (toolPresetParams != null && toolPresetParams.containsKey(mcpServerName)) {
                    Map<String, Map<String, String>> presetMap = toolPresetParams.get(mcpServerName);
                    if (presetMap != null && !presetMap.isEmpty()) {
                        Map<String, String> presetArgumentsByTool = new HashMap<>();
                        presetMap.forEach(
                                (toolName, params) -> presetArgumentsByTool.put(toolName, JsonUtils.toJsonString(params)));
                        mcpClient = new PresetParametersMcpClient(mcpClient, presetArgumentsByTool);
                    }
                }
                mcpClients.add(mcpClient);
            } catch (Exception e) {
                // One unavailable MCP must not make ordinary chat fail. Other
                // configured tools can still serve the request, and the next
                // chat can retry the unavailable service.
                logger.warn("Skipping unavailable MCP tool {}: {}", mcpServerName, e.getMessage());
            }
        }

        if (mcpClients.isEmpty()) {
            return null;
        }

        ToolProvider delegate = McpToolProvider.builder().mcpClients(mcpClients).build();
        return new CloseableToolProvider(wrapWithRepeatedCallGuard(delegate), mcpClients);
    }

    public ToolProvider createToolProvider(ChatContext chatContext) {
        List<String> availableTools = getAvailableTools(chatContext);
        if (availableTools == null || availableTools.isEmpty()) {
            return null;
        }

        return createToolProvider(availableTools,
                chatContext.getAgent() != null ? chatContext.getAgent().getToolPresetParams() : null,
                chatContext.getUserId());
    }

    public List<String> getAvailableTools(ChatContext chatContext) {
        if (chatContext == null || chatContext.getMcpServerNames() == null
                || chatContext.getMcpServerNames().isEmpty()) {
            return List.of();
        }

        String userMessage = normalize(chatContext.getUserMessage());
        if (!shouldPrepareAnyTools(userMessage)) {
            return List.of();
        }

        List<String> selectedTools = new ArrayList<>();
        for (String toolName : chatContext.getMcpServerNames()) {
            if (shouldPrepareTool(toolName, userMessage)) {
                selectedTools.add(toolName);
            }
        }
        return selectedTools;
    }

    private boolean shouldPrepareAnyTools(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isEmpty()) {
            return false;
        }
        if (LIGHTWEIGHT_MESSAGES.contains(normalizedMessage)) {
            return false;
        }
        if (normalizedMessage.matches("[0-9\\s+\\-*/().=？?]+")) {
            return false;
        }
        return true;
    }

    private boolean shouldPrepareTool(String toolName, String normalizedMessage) {
        String normalizedToolName = toolName == null ? "" : toolName.toLowerCase();

        if (normalizedToolName.contains("aliyunbailianmcp_market") || normalizedToolName.contains("weather")
                || normalizedToolName.contains("天气")) {
            return containsAny(normalizedMessage, WEATHER_HINTS);
        }

        if (normalizedToolName.contains("amap") || normalizedToolName.contains("maps")
                || normalizedToolName.contains("地图") || normalizedToolName.contains("高德")) {
            return containsAny(normalizedMessage, MAP_HINTS);
        }

        if (normalizedToolName.contains("surge")) {
            return containsAny(normalizedMessage, SURGE_HINTS);
        }

        return true;
    }

    private boolean containsAny(String text, Set<String> hints) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (String hint : hints) {
            if (text.contains(hint.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String message) {
        return message == null ? "" : message.trim().toLowerCase();
    }

    private ToolProvider wrapWithRepeatedCallGuard(ToolProvider delegate) {
        Map<String, String> successfulResults = new ConcurrentHashMap<>();
        AtomicInteger successfulWeatherToolCalls = new AtomicInteger(0);
        AtomicReference<String> latestWeatherResult = new AtomicReference<>("");
        return request -> {
            ToolProviderResult result = delegate.provideTools(request);
            if (result == null || result.tools() == null || result.tools().isEmpty()) {
                return result;
            }

            Map<dev.langchain4j.agent.tool.ToolSpecification, ToolExecutor> guardedTools = new HashMap<>();
            result.tools().forEach((specification, executor) -> guardedTools.put(specification,
                    wrapToolExecutor(executor, successfulResults, successfulWeatherToolCalls, latestWeatherResult)));
            return ToolProviderResult.builder().addAll(guardedTools).build();
        };
    }

    private ToolExecutor wrapToolExecutor(ToolExecutor delegate, Map<String, String> successfulResults,
            AtomicInteger successfulWeatherToolCalls, AtomicReference<String> latestWeatherResult) {
        return (toolExecutionRequest, memoryId) -> {
            String executionKey = buildExecutionKey(toolExecutionRequest);
            String cachedResult = successfulResults.get(executionKey);
            if (cachedResult != null && !cachedResult.isBlank()) {
                return cachedResult + REPEATED_TOOL_RESULT_HINT;
            }

            boolean weatherToolRequest = isWeatherToolRequest(toolExecutionRequest);
            if (weatherToolRequest && successfulWeatherToolCalls.get() >= MAX_SUCCESSFUL_WEATHER_TOOL_CALLS_PER_CHAT) {
                String latestResult = latestWeatherResult.get();
                if (latestResult != null && !latestResult.isBlank()) {
                    return latestResult + WEATHER_TOOL_LIMIT_HINT;
                }
                return WEATHER_TOOL_LIMIT_HINT.trim();
            }

            String result = delegate.execute(toolExecutionRequest, memoryId);
            if (isSuccessfulToolResult(result)) {
                successfulResults.put(executionKey, result);
                if (weatherToolRequest) {
                    successfulWeatherToolCalls.incrementAndGet();
                    latestWeatherResult.set(result);
                }
            }
            return result;
        };
    }

    private boolean isWeatherToolRequest(ToolExecutionRequest toolExecutionRequest) {
        if (toolExecutionRequest == null) {
            return false;
        }

        String normalizedName = normalize(toolExecutionRequest.name());
        if (containsAny(normalizedName, WEATHER_HINTS) || normalizedName.contains("weather")) {
            return true;
        }

        String normalizedArguments = normalize(JsonUtils.toJsonString(toolExecutionRequest.arguments()));
        return containsAny(normalizedArguments, WEATHER_HINTS) || normalizedArguments.contains("weather");
    }

    private String buildExecutionKey(ToolExecutionRequest toolExecutionRequest) {
        if (toolExecutionRequest == null) {
            return "";
        }
        return toolExecutionRequest.name() + "::" + JsonUtils.toJsonString(toolExecutionRequest.arguments());
    }

    private boolean isSuccessfulToolResult(String result) {
        if (result == null || result.isBlank()) {
            return false;
        }
        String normalized = result.toLowerCase();
        return !normalized.contains("timeout executing the tool")
                && !normalized.contains("there was a timeout executing the tool")
                && !normalized.contains("sse channel failure") && !normalized.contains("sockettimeoutexception")
                && !normalized.contains("\"error\"") && !normalized.contains("exception");
    }

    /** MCP 客户端会持有长连接，必须在本次对话结束后释放。ToolProvider 本身不声明关闭能力， 因此用同时实现 AutoCloseable 的包装器把资源所有权显式传递给消息处理器。 */
    static final class CloseableToolProvider implements ToolProvider, AutoCloseable {

        private final ToolProvider delegate;
        private final List<McpClient> clients;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        CloseableToolProvider(ToolProvider delegate, List<McpClient> clients) {
            this.delegate = delegate;
            this.clients = List.copyOf(clients);
        }

        @Override
        public ToolProviderResult provideTools(dev.langchain4j.service.tool.ToolProviderRequest request) {
            return delegate.provideTools(request);
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }

            for (McpClient client : clients) {
                try {
                    client.close();
                } catch (Exception e) {
                    logger.debug("Failed to close MCP client {}: {}", client.key(), e.getMessage());
                }
            }
        }
    }
}
