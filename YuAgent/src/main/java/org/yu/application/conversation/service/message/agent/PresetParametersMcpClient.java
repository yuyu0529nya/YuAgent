package org.yu.application.conversation.service.message.agent;

import java.util.List;
import java.util.Map;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpPrompt;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.mcp.client.McpResourceTemplate;
import dev.langchain4j.mcp.client.McpRoot;
import dev.langchain4j.service.tool.ToolExecutionResult;

/** Preset tool parameters for an MCP server.
 *
 * <p>
 * The project used to get this feature from a custom langchain4j fork ({@code McpClient#presetParameters}). With the
 * official langchain4j artifacts, the same behavior is kept by wrapping the client: when a tool with configured preset
 * parameters is called, the model-supplied arguments are replaced by the preset JSON, exactly like the fork did. */
public class PresetParametersMcpClient implements McpClient {

    private final McpClient delegate;
    private final Map<String, String> presetArgumentsByTool;

    public PresetParametersMcpClient(McpClient delegate, Map<String, String> presetArgumentsByTool) {
        this.delegate = delegate;
        this.presetArgumentsByTool = presetArgumentsByTool;
    }

    @Override
    public String key() {
        return delegate.key();
    }

    @Override
    public String instructions() {
        return delegate.instructions();
    }

    @Override
    public List<ToolSpecification> listTools() {
        return delegate.listTools();
    }

    @Override
    public List<ToolSpecification> listTools(InvocationContext invocationContext) {
        return delegate.listTools(invocationContext);
    }

    @Override
    public ToolExecutionResult executeTool(ToolExecutionRequest executionRequest) {
        return delegate.executeTool(withPresetArguments(executionRequest));
    }

    @Override
    public ToolExecutionResult executeTool(ToolExecutionRequest executionRequest, InvocationContext invocationContext) {
        return delegate.executeTool(withPresetArguments(executionRequest), invocationContext);
    }

    private ToolExecutionRequest withPresetArguments(ToolExecutionRequest executionRequest) {
        String presetArguments = presetArgumentsByTool.get(executionRequest.name());
        if (presetArguments == null) {
            return executionRequest;
        }
        return ToolExecutionRequest.builder().id(executionRequest.id()).name(executionRequest.name())
                .arguments(presetArguments).build();
    }

    @Override
    public List<McpResource> listResources() {
        return delegate.listResources();
    }

    @Override
    public List<McpResource> listResources(InvocationContext invocationContext) {
        return delegate.listResources(invocationContext);
    }

    @Override
    public List<McpResourceTemplate> listResourceTemplates() {
        return delegate.listResourceTemplates();
    }

    @Override
    public List<McpResourceTemplate> listResourceTemplates(InvocationContext invocationContext) {
        return delegate.listResourceTemplates(invocationContext);
    }

    @Override
    public McpReadResourceResult readResource(String uri) {
        return delegate.readResource(uri);
    }

    @Override
    public McpReadResourceResult readResource(String uri, InvocationContext invocationContext) {
        return delegate.readResource(uri, invocationContext);
    }

    @Override
    @Deprecated
    public void subscribeToResource(String uri) {
        delegate.subscribeToResource(uri);
    }

    @Override
    @Deprecated
    public void unsubscribeFromResource(String uri) {
        delegate.unsubscribeFromResource(uri);
    }

    @Override
    public List<McpPrompt> listPrompts() {
        return delegate.listPrompts();
    }

    @Override
    public McpGetPromptResult getPrompt(String name, Map<String, Object> arguments) {
        return delegate.getPrompt(name, arguments);
    }

    @Override
    public void checkHealth() {
        delegate.checkHealth();
    }

    @Override
    @Deprecated
    public void setRoots(List<McpRoot> roots) {
        delegate.setRoots(roots);
    }

    @Override
    public void close() throws Exception {
        delegate.close();
    }
}
