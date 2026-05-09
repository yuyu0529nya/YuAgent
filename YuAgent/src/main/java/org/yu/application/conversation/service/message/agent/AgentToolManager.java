package org.yu.application.conversation.service.message.agent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.PresetParameter;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.springframework.stereotype.Component;
import org.yu.application.conversation.service.McpUrlProviderService;
import org.yu.application.conversation.service.handler.context.ChatContext;
import org.yu.infrastructure.utils.JsonUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class AgentToolManager {

    private static final Duration MCP_TOOL_TIMEOUT = Duration.ofSeconds(12);
    private static final String REPEATED_TOOL_RESULT_HINT =
            "\n\n[系统提示] 这个工具的相同参数结果在本轮对话中已经返回过了。请直接基于上面的结果完成回答，不要继续重复调用同一个工具。";
    private static final int MAX_SUCCESSFUL_WEATHER_TOOL_CALLS_PER_CHAT = 2;
    private static final String WEATHER_TOOL_LIMIT_HINT =
            "\n\n[系统提示] 本轮对话已经获取到足够的天气数据。对于同一城市或同一趟旅行行程，不要继续按景点逐个查询天气，请直接基于已有天气结果给出完整回答。";

    private static final Set<String> LIGHTWEIGHT_MESSAGES = Set.of(
            "你好", "您好", "hi", "hello", "hey", "在吗", "在么", "嗨", "hello?");

    private static final Set<String> WEATHER_HINTS = Set.of(
            "天气", "气温", "温度", "下雨", "降雨", "预报", "风力", "湿度", "空气质量", "紫外线",
            "实时天气", "未来天气", "今日天气", "明天天气", "后天天气", "穿衣", "出行", "旅游", "行程",
            "沈阳", "北京", "上海", "广州", "深圳", "杭州", "南京", "青岛", "成都", "重庆");

    private static final Set<String> SURGE_HINTS = Set.of(
            "surge", "部署", "发布", "上线", "login", "登录", "domain", "域名");

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
        for (String mcpServerName : mcpServerNames) {
            String sseUrl = mcpUrlProviderService.getMcpToolUrl(mcpServerName, userId);
            McpTransport transport = new HttpMcpTransport.Builder()
                    .sseUrl(sseUrl)
                    .logRequests(false)
                    .logResponses(false)
                    .timeout(MCP_TOOL_TIMEOUT)
                    .build();

            McpClient mcpClient = new DefaultMcpClient.Builder().transport(transport).build();
            if (toolPresetParams != null && toolPresetParams.containsKey(mcpServerName)) {
                List<PresetParameter> presetParameters = new ArrayList<>();
                Map<String, Map<String, String>> presetMap = toolPresetParams.get(mcpServerName);
                if (presetMap != null) {
                    presetMap.forEach((toolName, params) ->
                            presetParameters.add(new PresetParameter(toolName, JsonUtils.toJsonString(params))));
                }
                if (!presetParameters.isEmpty()) {
                    mcpClient.presetParameters(presetParameters);
                }
            }
            mcpClients.add(mcpClient);
        }

        ToolProvider delegate = McpToolProvider.builder().mcpClients(mcpClients).build();
        return wrapWithRepeatedCallGuard(delegate);
    }

    public ToolProvider createToolProvider(ChatContext chatContext) {
        List<String> availableTools = getAvailableTools(chatContext);
        if (availableTools == null || availableTools.isEmpty()) {
            return null;
        }

        return createToolProvider(
                availableTools,
                chatContext.getAgent() != null ? chatContext.getAgent().getToolPresetParams() : null,
                chatContext.getUserId());
    }

    public List<String> getAvailableTools(ChatContext chatContext) {
        if (chatContext == null || chatContext.getMcpServerNames() == null || chatContext.getMcpServerNames().isEmpty()) {
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

        if (normalizedToolName.contains("aliyunbailianmcp_market")
                || normalizedToolName.contains("weather")
                || normalizedToolName.contains("天气")) {
            return containsAny(normalizedMessage, WEATHER_HINTS);
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
            result.tools().forEach((specification, executor) ->
                    guardedTools.put(specification, wrapToolExecutor(executor, successfulResults,
                            successfulWeatherToolCalls, latestWeatherResult)));
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
                && !normalized.contains("sse channel failure")
                && !normalized.contains("sockettimeoutexception")
                && !normalized.contains("\"error\"")
                && !normalized.contains("exception");
    }
}
