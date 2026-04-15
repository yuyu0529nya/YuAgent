package bridge

import (
	"context"
	"fmt"
	"time"

	"github.com/lucky-aeon/agentx/plugin-helper/xlog"
	client "github.com/mark3labs/mcp-go/client"
	"github.com/mark3labs/mcp-go/client/transport"
	"github.com/mark3labs/mcp-go/mcp"
	server "github.com/mark3labs/mcp-go/server"
)

// HTTPStreamToSSEBridge bridges an upstream Streamable HTTP MCP service to a local SSE endpoint.
type HTTPStreamToSSEBridge struct {
	httpClient *client.Client
	mcpServer  *server.MCPServer
	*server.SSEServer
	mcpName string
	logger  xlog.Logger
}

func NewHTTPStreamToSSEBridge(ctx context.Context, upstreamBaseURL string, mcpName string) (*HTTPStreamToSSEBridge, error) {
	logger := xlog.NewLogger("bridge").With("mcp_name", mcpName)

	httpTransport, err := transport.NewStreamableHTTP(upstreamBaseURL)
	if err != nil {
		logger.Error("Failed to create streamable HTTP transport", "error", err, "base_url", upstreamBaseURL)
		return nil, fmt.Errorf("failed to create streamable HTTP transport: %w", err)
	}

	httpClient := client.NewClient(httpTransport)

	logger.Info("Starting streamable HTTP client", "mcp_name", mcpName, "base_url", upstreamBaseURL)
	startCtx, cancelStart := context.WithTimeout(ctx, 20*time.Second)
	defer cancelStart()
	if err := httpClient.Start(startCtx); err != nil {
		logger.Error("Failed to start streamable HTTP client", "error", err)
		return nil, fmt.Errorf("failed to start streamable HTTP client: %w", err)
	}

	initRequest := mcp.InitializeRequest{}
	initRequest.Params.ProtocolVersion = mcp.LATEST_PROTOCOL_VERSION
	initRequest.Params.ClientInfo = mcp.Implementation{
		Name:    "mcp-http-stream-sse-bridge",
		Version: "1.0.0",
	}

	initCtx, cancelInit := context.WithTimeout(ctx, 20*time.Second)
	defer cancelInit()
	initResult, err := httpClient.Initialize(initCtx, initRequest)
	if err != nil {
		logger.Error("Failed to initialize streamable HTTP client", "error", err)
		return nil, fmt.Errorf("failed to initialize streamable HTTP client: %w", err)
	}

	logger.Info("Connected to streamable HTTP server",
		"server_name", initResult.ServerInfo.Name,
		"server_version", initResult.ServerInfo.Version,
	)

	mcpServer := server.NewMCPServer(
		initResult.ServerInfo.Name,
		initResult.ServerInfo.Version,
		server.WithToolCapabilities(true),
		server.WithResourceCapabilities(true, true),
		server.WithPromptCapabilities(true),
	)

	bridge := &HTTPStreamToSSEBridge{
		httpClient: httpClient,
		mcpServer:  mcpServer,
		mcpName:    mcpName,
		logger:     logger,
	}

	if err := bridge.setupToolBridge(ctx); err != nil {
		bridge.logger.Warn("Failed to setup tool bridge", "error", err)
	}
	if err := bridge.setupResourceBridge(ctx); err != nil {
		bridge.logger.Warnf("Resource bridging failed (server may not support resources): %v", err)
	}
	if err := bridge.setupPromptBridge(ctx); err != nil {
		bridge.logger.Warnf("Prompt bridging failed (server may not support prompts): %v", err)
	}

	sseServer := server.NewSSEServer(
		mcpServer,
		server.WithStaticBasePath(mcpName),
		server.WithSSEEndpoint("/sse"),
		server.WithMessageEndpoint("/message"),
	)

	bridge.SSEServer = sseServer
	return bridge, nil
}

func (b *HTTPStreamToSSEBridge) setupToolBridge(ctx context.Context) error {
	listCtx, cancelList := context.WithTimeout(ctx, 20*time.Second)
	defer cancelList()

	toolsResult, err := b.httpClient.ListTools(listCtx, mcp.ListToolsRequest{})
	if err != nil {
		return fmt.Errorf("failed to list tools from streamable HTTP server: %w", err)
	}

	for _, tool := range toolsResult.Tools {
		bridgedTool := tool
		toolName := tool.Name

		b.mcpServer.AddTool(bridgedTool, func(ctx context.Context, request mcp.CallToolRequest) (*mcp.CallToolResult, error) {
			callCtx, cancelCall := context.WithTimeout(ctx, 20*time.Second)
			defer cancelCall()

			result, err := b.httpClient.CallTool(callCtx, request)
			if err != nil {
				b.logger.Error("Tool call failed", "tool_name", toolName, "error", err)
				return mcp.NewToolResultError(fmt.Sprintf("Failed to call tool %s: %v", toolName, err)), nil
			}
			return result, nil
		})
	}
	return nil
}

func (b *HTTPStreamToSSEBridge) setupResourceBridge(ctx context.Context) error {
	resourceCtx, cancelResource := context.WithTimeout(ctx, 20*time.Second)
	defer cancelResource()

	resourcesResult, err := b.httpClient.ListResources(resourceCtx, mcp.ListResourcesRequest{})
	if err != nil {
		return fmt.Errorf("failed to list resources from streamable HTTP server: %w", err)
	}

	for _, resource := range resourcesResult.Resources {
		bridgedResource := resource
		resourceURI := resource.URI

		b.mcpServer.AddResource(bridgedResource, func(ctx context.Context, request mcp.ReadResourceRequest) ([]mcp.ResourceContents, error) {
			readCtx, cancelRead := context.WithTimeout(ctx, 20*time.Second)
			defer cancelRead()

			result, err := b.httpClient.ReadResource(readCtx, request)
			if err != nil {
				b.logger.Error("Resource read failed", "resource_uri", resourceURI, "error", err)
				return nil, fmt.Errorf("failed to read resource %s: %w", resourceURI, err)
			}
			return result.Contents, nil
		})
	}

	templateCtx, cancelTemplate := context.WithTimeout(ctx, 20*time.Second)
	defer cancelTemplate()

	templatesResult, err := b.httpClient.ListResourceTemplates(templateCtx, mcp.ListResourceTemplatesRequest{})
	if err != nil {
		return fmt.Errorf("failed to list resource templates from streamable HTTP server: %w", err)
	}

	for _, template := range templatesResult.ResourceTemplates {
		bridgedTemplate := template
		templateURI := template.URITemplate

		b.mcpServer.AddResourceTemplate(bridgedTemplate, func(ctx context.Context, request mcp.ReadResourceRequest) ([]mcp.ResourceContents, error) {
			readCtx, cancelRead := context.WithTimeout(ctx, 20*time.Second)
			defer cancelRead()

			result, err := b.httpClient.ReadResource(readCtx, request)
			if err != nil {
				b.logger.Error("Resource template read failed", "template_uri", templateURI, "error", err)
				return nil, fmt.Errorf("failed to read resource template %s: %w", templateURI, err)
			}
			return result.Contents, nil
		})
	}
	return nil
}

func (b *HTTPStreamToSSEBridge) setupPromptBridge(ctx context.Context) error {
	promptCtx, cancelPrompt := context.WithTimeout(ctx, 20*time.Second)
	defer cancelPrompt()

	promptsResult, err := b.httpClient.ListPrompts(promptCtx, mcp.ListPromptsRequest{})
	if err != nil {
		return fmt.Errorf("failed to list prompts from streamable HTTP server: %w", err)
	}

	for _, prompt := range promptsResult.Prompts {
		bridgedPrompt := prompt
		promptName := prompt.Name

		b.mcpServer.AddPrompt(bridgedPrompt, func(ctx context.Context, request mcp.GetPromptRequest) (*mcp.GetPromptResult, error) {
			getPromptCtx, cancelPrompt := context.WithTimeout(ctx, 20*time.Second)
			defer cancelPrompt()

			result, err := b.httpClient.GetPrompt(getPromptCtx, request)
			if err != nil {
				b.logger.Error("Prompt get failed", "prompt_name", promptName, "error", err)
				return nil, fmt.Errorf("failed to get prompt %s: %w", promptName, err)
			}
			return result, nil
		})
	}

	return nil
}

func (b *HTTPStreamToSSEBridge) Start(addr string) error {
	b.logger.Info("Starting HTTP stream to SSE bridge server", "address", addr)
	if err := b.Ping(context.Background()); err != nil {
		return fmt.Errorf("failed to ping streamable HTTP server: %w", err)
	}
	return b.SSEServer.Start(addr)
}

func (b *HTTPStreamToSSEBridge) Close() error {
	if b.httpClient != nil {
		_ = b.httpClient.Close()
	}
	return b.SSEServer.Shutdown(context.Background())
}

func (b *HTTPStreamToSSEBridge) Ping(ctx context.Context) error {
	if b.httpClient == nil {
		return fmt.Errorf("streamable HTTP client is not initialized")
	}
	pingCtx, cancelPing := context.WithTimeout(ctx, 10*time.Second)
	defer cancelPing()
	return b.httpClient.Ping(pingCtx)
}
