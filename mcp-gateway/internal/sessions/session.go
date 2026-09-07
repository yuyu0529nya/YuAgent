// Session
// 用于存储会话状态，包括接收的消息和处理结果
package sessions

import (
	"context"
	"encoding/json"
	"fmt"
	"sort"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/lucky-aeon/agentx/plugin-helper/internal/platform/xlog"
	"github.com/mark3labs/mcp-go/client"
	"github.com/mark3labs/mcp-go/client/transport"
	"github.com/mark3labs/mcp-go/mcp"
)

type McpName = string
type McpToolName = string

const (
	// 无活跃连接时，超过该时间自动释放 session
	sessionNoConnectionTTL = 1 * time.Minute
	// session 不活跃检查间隔
	sessionInactivityCheckInterval = 10 * time.Second
	// 下游客户端启动和就绪检查都必须有上限，避免不可用的远端服务长期占用请求协程。
	mcpClientStartTimeout      = 10 * time.Second
	mcpClientReadyTimeout      = 5 * time.Second
	mcpClientInitializeTimeout = 30 * time.Second
	mcpToolsListTimeout        = 30 * time.Second
	mcpMessageSendTimeout      = 10 * time.Second
)

const remoteOAuthAccessTokenEnv = "MCP_REMOTE_AUTH_ACCESS_TOKEN"

type CleanupConfig struct {
	NoConnectionTTL         time.Duration
	InactivityCheckInterval time.Duration
}

func normalizeCleanupConfig(cfg CleanupConfig) CleanupConfig {
	if cfg.NoConnectionTTL <= 0 {
		cfg.NoConnectionTTL = sessionNoConnectionTTL
	}
	if cfg.InactivityCheckInterval <= 0 {
		cfg.InactivityCheckInterval = sessionInactivityCheckInterval
	}
	return cfg
}

type Session struct {
	// 使用单一主锁减少死锁风险
	mu sync.RWMutex
	// Serializes duplicate detection and non-blocking event broadcasts. Without
	// this lock, two downstream callbacks can both observe the same previous
	// lastMsg and forward a duplicate JSON-RPC response.
	eventSendMu sync.Mutex

	Id              string
	CreatedAt       time.Time // 会话创建时间
	LastReceiveTime time.Time // 最后一次接收消息的时间

	// SSE事件通道 - 由主锁保护
	eventChans []chan SessionMsg
	doneChan   chan struct{}
	// session清理配置
	cleanupConfig CleanupConfig

	// 清理机制
	cleanupCallback func(sessionId string) // 清理回调函数

	// 工具映射 - 由主锁保护
	mcpToolsMap       map[McpName]map[McpToolName]mcp.Tool
	toolsListMu       sync.Mutex  // 序列化 tools/list 聚合，避免并发请求覆盖彼此状态
	aggregatedTools   []mcp.Tool  // 聚合后的工具列表，工具名带MCP前缀
	toolsListComplete atomic.Bool // 标记工具列表是否已完成聚合

	// 避免重复返回 - 由主锁保护
	lastMsg SessionMsg

	// V2
	mcpClients           map[McpName]client.MCPClient
	mcpClientCancels     map[McpName]context.CancelFunc
	mcpinitializeResults map[McpName]*mcp.InitializeResult
}

func NewSession(id string) *Session {
	return newSession(id, CleanupConfig{})
}

func newSession(id string, cleanupConfig CleanupConfig) *Session {
	now := time.Now()
	cleanupConfig = normalizeCleanupConfig(cleanupConfig)
	session := &Session{
		Id:                   id,
		CreatedAt:            now,
		LastReceiveTime:      now,
		eventChans:           make([]chan SessionMsg, 0),
		doneChan:             make(chan struct{}),
		cleanupConfig:        cleanupConfig,
		mcpToolsMap:          make(map[McpName]map[McpToolName]mcp.Tool),
		aggregatedTools:      make([]mcp.Tool, 0),
		toolsListComplete:    atomic.Bool{},
		mcpClients:           make(map[McpName]client.MCPClient),
		mcpClientCancels:     make(map[McpName]context.CancelFunc),
		mcpinitializeResults: make(map[McpName]*mcp.InitializeResult),
	}

	// 启动监控协程
	go session.startInactivityMonitor()

	return session
}

// SetCleanupCallback 设置清理回调函数
func (s *Session) SetCleanupCallback(callback func(sessionId string)) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.cleanupCallback = callback
}

// startInactivityMonitor 启动不活跃监控
func (s *Session) startInactivityMonitor() {
	ticker := time.NewTicker(s.cleanupConfig.InactivityCheckInterval)
	defer ticker.Stop()

	for {
		select {
		case <-s.doneChan:
			return
		case <-ticker.C:
			s.checkInactivity()
		}
	}
}

// checkInactivity 检查session是否应该被清理
func (s *Session) checkInactivity() {
	s.mu.RLock()
	hasActiveChans := len(s.eventChans) > 0
	lastActivity := s.LastReceiveTime
	cleanupCallback := s.cleanupCallback
	sessionId := s.Id
	s.mu.RUnlock()

	// 如果没有活跃的事件通道且超过阈值没有活动，则清理 session
	if !hasActiveChans && time.Since(lastActivity) > s.cleanupConfig.NoConnectionTTL {
		xl := xlog.NewLogger("session-monitor")
		xl.Infof("Session %s is inactive, triggering cleanup", sessionId)

		if cleanupCallback != nil {
			cleanupCallback(sessionId)
		}
	}
}

func (s *Session) GetId() string {
	return s.Id
}

func (s *Session) SendMessage(xl xlog.Logger, content json.RawMessage) (err error) {
	ctx, cancel := context.WithTimeout(context.Background(), mcpMessageSendTimeout)
	defer cancel()
	return s.SendMessageContext(ctx, xl, content)
}

// SendMessageContext forwards a JSON-RPC message while honoring the caller's
// cancellation and deadline. The legacy SendMessage method keeps its bounded
// timeout for callers that do not have an inbound request context.
func (s *Session) SendMessageContext(ctx context.Context, xl xlog.Logger, content json.RawMessage) (err error) {
	if ctx == nil {
		ctx = context.Background()
	}
	// 发送消息到 MCP 服务
	var request mcp.JSONRPCRequest
	if err = json.Unmarshal([]byte(content), &request); err != nil {
		xl.Errorf("failed to unmarshal request: %v", err)
		return fmt.Errorf("failed to unmarshal request: %w", err)
	}
	method := request.Method
	xl = xlog.WithChildName(method, xl)

	xl.Debugf("Forwarding request: method=%s id=%v", request.Method, request.ID)

	// xl.Infof("method: %s, content: %s", method, content)
	var singleMcp McpName
	switch mcp.MCPMethod(request.Method) {
	case mcp.MethodToolsCall:
		req := mcp.CallToolRequest{}
		err := json.Unmarshal([]byte(content), &req)
		if err != nil {
			xl.Errorf("failed to unmarshal request: %v", err)
			return fmt.Errorf("failed to unmarshal request: %w", err)
		}

		// Aggregated tool names are encoded as mcpName_toolName. MCP server
		// names may themselves contain underscores, so find the longest known
		// server-name prefix instead of splitting at the first underscore.
		s.mu.RLock()
		matchedName := ""
		for mcpName := range s.mcpClients {
			prefix := string(mcpName) + "_"
			if strings.HasPrefix(req.Params.Name, prefix) && len(prefix) > len(matchedName) {
				matchedName = prefix
				singleMcp = mcpName
			}
		}
		s.mu.RUnlock()
		if matchedName != "" {
			req.Params.Name = strings.TrimPrefix(req.Params.Name, matchedName)

			// 重新序列化请求以更新工具名
			updatedContent, err := json.Marshal(req)
			if err != nil {
				xl.Errorf("failed to marshal updated request: %v", err)
				return fmt.Errorf("failed to marshal updated request: %w", err)
			}
			content = updatedContent
		}
	}

	// 对所有 MCP 服务器发送消息
	if singleMcp == "" {
		// 如果是tools/list请求，需要特殊处理来聚合所有MCP的工具
		if method == "tools/list" {
			return s.handleToolsListRequest(xl, request)
		}

		// 其他请求照常处理
		s.mu.RLock()
		mcpNames := make([]McpName, 0, len(s.mcpClients))
		for mcpName := range s.mcpClients {
			mcpNames = append(mcpNames, mcpName)
		}
		s.mu.RUnlock()

		if len(mcpNames) == 0 {
			s.sendErrorResponse(request.ID, fmt.Errorf("no MCP services available"))
			return nil
		}

		for _, mcpName := range mcpNames {
			err = s.sendToMcpContext(ctx, xl, mcpName, request, content)
			if err != nil {
				xl.Errorf("failed to send to allmcp: %v", err)
				continue
			}
		}
	} else {
		// xl.Infof("send to single MCP server: %s, content: %s", singleMcp, content)
		err = s.sendToMcpContext(ctx, xl, singleMcp, request, content)
		if err != nil {
			xl.Errorf("failed to send to singlemcp: %v", err)
			return err
		}
	}

	return nil
}

func (s *Session) sendToMcp(xl xlog.Logger, mcpName McpName, baseReq mcp.JSONRPCRequest, reqRaw json.RawMessage) error {
	ctx, cancel := context.WithTimeout(context.Background(), mcpMessageSendTimeout)
	defer cancel()
	return s.sendToMcpContext(ctx, xl, mcpName, baseReq, reqRaw)
}

func (s *Session) sendToMcpContext(ctx context.Context, xl xlog.Logger, mcpName McpName, baseReq mcp.JSONRPCRequest, reqRaw json.RawMessage) error {
	xl = xlog.WithChildName(mcpName, xl)
	isNotification := baseReq.ID.IsNil() || strings.HasPrefix(baseReq.Method, "notifications/")

	s.mu.RLock()
	mCli, ok := s.mcpClients[mcpName]
	s.mu.RUnlock()
	if !ok {
		err := fmt.Errorf("failed to find mcpClient for %s", mcpName)
		xl.Error(err)
		return err
	}

	result, err := s.handleMCPMethod(ctx, xl, mCli, mcpName, baseReq.Method, reqRaw)
	if err != nil {
		if isNotification {
			xl.Warnf("Ignore notification %s error: %v", baseReq.Method, err)
			return nil
		}
		xl.Errorf("failed to call MCP method %s: %v", baseReq.Method, err)
		s.sendErrorResponse(baseReq.ID, err)
		return err
	}

	if result != nil && !isNotification {
		s.sendSuccessResponse(baseReq.ID, result)
	}

	return nil
}

// SubscribeSSE 订阅MCP服务的SSE事件
func (s *Session) SubscribeSSE(xl xlog.Logger, mcpName McpName, sseUrl string, headers map[string]string) error {
	options := []transport.ClientOption{}
	if len(headers) > 0 {
		options = append(options, client.WithHeaders(headers))
	}
	cli, err := client.NewSSEMCPClient(sseUrl, options...)
	if err != nil {
		return fmt.Errorf("failed to create SSE client: %w", err)
	}
	return s.subscribeMCPClient(xl, mcpName, cli, "SSE")
}

// SubscribeStreamHTTP 订阅 Streamable HTTP MCP 服务。
func (s *Session) SubscribeStreamHTTP(xl xlog.Logger, mcpName McpName, streamURL string, headers map[string]string) error {
	options := []transport.StreamableHTTPCOption{}
	if len(headers) > 0 {
		options = append(options, transport.WithHTTPHeaders(headers))
	}
	cli, err := client.NewStreamableHttpClient(streamURL, options...)
	if err != nil {
		return fmt.Errorf("failed to create Streamable HTTP client: %w", err)
	}
	return s.subscribeMCPClient(xl, mcpName, cli, "Streamable HTTP")
}

func (s *Session) subscribeMCPClient(xl xlog.Logger, mcpName McpName, cli *client.Client, protocol string) error {
	startCancel, err := startMCPClient(cli)
	if err != nil {
		_ = cli.Close()
		return fmt.Errorf("failed to start %s client: %w", protocol, err)
	}

	// Hosted Streamable HTTP services can spend more than ten seconds on their
	// first connection.  Keep the downstream client alive long enough to finish
	// its handshake instead of silently omitting its tools from the gateway.
	ctx, cancel := context.WithTimeout(context.Background(), mcpClientInitializeTimeout)
	defer cancel()

	result, err := cli.Initialize(ctx, mcp.InitializeRequest{
		Params: mcp.InitializeParams{
			ProtocolVersion: mcp.LATEST_PROTOCOL_VERSION,
			ClientInfo: mcp.Implementation{
				Name:    "mcp-gateway-client",
				Version: "1.0.0",
			},
		},
	})
	if err != nil {
		startCancel()
		_ = cli.Close()
		return fmt.Errorf("failed to initialize %s client: %w", protocol, err)
	}

	if err = cli.Ping(ctx); err != nil {
		startCancel()
		_ = cli.Close()
		return fmt.Errorf("failed to ping %s client: %w", protocol, err)
	}

	xl.Infof("%s client initialized and connected successfully", protocol)

	// 优化：批量更新状态，减少锁竞争
	s.mu.Lock()
	s.mcpClients[mcpName] = cli
	s.mcpClientCancels[mcpName] = startCancel
	s.mcpinitializeResults[mcpName] = result
	s.mu.Unlock()

	return nil
}

func startMCPClient(cli *client.Client) (context.CancelFunc, error) {
	clientContext, cancel := context.WithCancel(context.Background())
	startResult := make(chan error, 1)
	go func() {
		startResult <- cli.Start(clientContext)
	}()

	select {
	case err := <-startResult:
		if err != nil {
			cancel()
		}
		return cancel, err
	case <-time.After(mcpClientStartTimeout):
		cancel()
		return nil, fmt.Errorf("MCP client start timed out after %s", mcpClientStartTimeout)
	}
}

type SessionMsg struct {
	proxyId  int64
	clientId int64
	Event    string `json:"event"`
	Data     string `json:"data"`
}

// check lastMsg is 重复的
func (smsg *SessionMsg) isDuplicate(newMsg *SessionMsg) bool {
	if smsg.proxyId != 0 && smsg.proxyId == newMsg.proxyId {
		return true
	}
	if smsg.clientId != 0 && smsg.clientId == newMsg.clientId {
		return true
	}
	if smsg.Data == newMsg.Data {
		return true
	}
	return false
}

// Close 关闭会话
func (s *Session) Close() {
	xl := xlog.NewLogger("session-" + s.Id)
	xl.Infof("Closing session: %s", s.Id)

	s.mu.Lock()
	defer s.mu.Unlock()

	// 关闭监控协程
	select {
	case <-s.doneChan:
		// 已经关闭
	default:
		close(s.doneChan)
	}

	// 关闭所有MCP客户端
	for mcpName, client := range s.mcpClients {
		xl.Infof("Closing MCP client: %s", mcpName)
		if cancel := s.mcpClientCancels[mcpName]; cancel != nil {
			cancel()
		}
		if err := client.Close(); err != nil {
			xl.Errorf("Error closing MCP client %s: %v", mcpName, err)
		}
	}
	s.mcpClientCancels = nil

	// 关闭所有事件通道
	for i, ch := range s.eventChans {
		xl.Infof("Closing event channel %d", i)
		close(ch)
	}
	s.eventChans = nil

	xl.Infof("Session closed: %s", s.Id)
}

// SendEvent 发送SSE事件
func (s *Session) SendEvent(event SessionMsg) {
	xl := xlog.NewLogger("session-" + s.Id)
	xl.Infof("Sending event: type=%s, bytes=%d", event.Event, len(event.Data))

	s.eventSendMu.Lock()
	defer s.eventSendMu.Unlock()

	s.mu.RLock()
	isDuplicate := s.lastMsg.isDuplicate(&event)
	if isDuplicate {
		s.mu.RUnlock()
		xl.Debugf("Event already sent: %s", event.Event)
		return
	}

	// Keep the read lock while sending so Close/remove cannot close a channel
	// between selecting it and sending to it.
	totalChannels := len(s.eventChans)
	sentToChannels := s.broadcastEvent(s.eventChans, event, xl)
	s.mu.RUnlock()

	// 只在成功发送后更新状态
	if sentToChannels > 0 {
		s.mu.Lock()
		s.LastReceiveTime = time.Now()
		s.lastMsg = event
		s.mu.Unlock()
		xl.Infof("Event sent to %d channels", sentToChannels)
	} else {
		xl.Warnf("Event not sent to any channels (total channels: %d)", totalChannels)
	}
}

// broadcastEvent 顺序广播事件到所有通道，避免为每个channel创建goroutine
func (s *Session) broadcastEvent(eventChans []chan SessionMsg, event SessionMsg, xl xlog.Logger) int {
	sentCount := 0

	for i, eventChan := range eventChans {
		select {
		case eventChan <- event:
			sentCount++
			xl.Debugf("Sent event to channel %d", i)
		default:
			xl.Warnf("Channel %d is full, dropping event", i)
		}
	}

	return sentCount
}

// GetEventChan 获取事件通道
func (s *Session) GetEventChan() <-chan SessionMsg {
	s.mu.Lock()
	defer s.mu.Unlock()
	curChan := make(chan SessionMsg, 100)
	s.eventChans = append(s.eventChans, curChan)

	return curChan
}

// GetEventChanWithCloser 获取事件通道并返回关闭函数
func (s *Session) GetEventChanWithCloser() (<-chan SessionMsg, func()) {
	s.mu.Lock()
	defer s.mu.Unlock()
	curChan := make(chan SessionMsg, 100)
	s.eventChans = append(s.eventChans, curChan)

	closer := func() {
		// Per-request channels are owned by the session. Removing the channel is
		// enough for this connection; Session.Close owns closing remaining chans.
		s.removeEventChan(curChan)
	}

	return curChan, closer
}

// removeEventChan 从事件通道列表中移除指定通道
func (s *Session) removeEventChan(targetChan chan SessionMsg) {
	s.mu.Lock()
	var shouldScheduleCleanup bool
	var sessionId string

	for i, ch := range s.eventChans {
		if ch == targetChan {
			// 移除通道
			s.eventChans = append(s.eventChans[:i], s.eventChans[i+1:]...)
			break
		}
	}

	// 检查是否所有通道都已关闭
	if len(s.eventChans) == 0 {
		shouldScheduleCleanup = true
		// 从最后一个连接断开时开始计时
		s.LastReceiveTime = time.Now()
		sessionId = s.Id
	}
	s.mu.Unlock()

	if shouldScheduleCleanup {
		xl := xlog.NewLogger("session-" + sessionId)
		xl.Infof("No active event channels remaining, session %s will be cleaned after %s", sessionId, s.cleanupConfig.NoConnectionTTL)
	}
}

// GetMcpTools 获取指定 MCP 的所有工具
func (s *Session) GetMcpTools(mcpName McpName) map[McpToolName]mcp.Tool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	if tools, ok := s.mcpToolsMap[mcpName]; ok {
		// 创建一个副本以避免外部修改
		result := make(map[McpToolName]mcp.Tool)
		for k, v := range tools {
			result[k] = v
		}
		return result
	}
	return nil
}

// GetMcpTool 获取指定 MCP 的指定工具
func (s *Session) GetMcpTool(mcpName McpName, toolName McpToolName) (mcp.Tool, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	if tools, ok := s.mcpToolsMap[mcpName]; ok {
		if tool, ok := tools[toolName]; ok {
			return tool, true
		}
	}
	return mcp.Tool{}, false
}

// sendResponse 统一的响应发送方法
func (s *Session) sendResponse(requestId interface{}, result interface{}, err error) {
	var responseData []byte
	var marshalErr error

	reqId := mcp.NewRequestId(requestId)

	if err != nil {
		// 发送错误响应
		response := mcp.JSONRPCError{
			JSONRPC: "2.0",
			ID:      reqId,
			Error: struct {
				Code    int    `json:"code"`
				Message string `json:"message"`
				Data    any    `json:"data,omitempty"`
			}{
				Code:    mcp.INTERNAL_ERROR,
				Message: err.Error(),
			},
		}
		responseData, marshalErr = json.Marshal(response)
	} else {
		// 发送成功响应
		response := mcp.JSONRPCResponse{
			JSONRPC: "2.0",
			ID:      reqId,
			Result:  result,
		}
		responseData, marshalErr = json.Marshal(response)
	}

	if marshalErr != nil {
		xl := xlog.NewLogger("session-" + s.Id)
		xl.Errorf("failed to marshal response: %v", marshalErr)
		return
	}

	s.SendEvent(SessionMsg{
		Event: "message",
		Data:  string(responseData),
	})
}

// sendSuccessResponse 发送成功响应到SSE
func (s *Session) sendSuccessResponse(requestId interface{}, result interface{}) {
	s.sendResponse(requestId, result, nil)
}

// sendErrorResponse 发送错误响应到SSE
func (s *Session) sendErrorResponse(requestId interface{}, err error) {
	s.sendResponse(requestId, nil, err)
}

// handleToolsListRequest 并行查询所有 MCP 并聚合工具列表。
func (s *Session) handleToolsListRequest(xl xlog.Logger, request mcp.JSONRPCRequest) error {
	xl.Debugf("Handling tools list request for all MCPs")
	go s.handleAllToolsRequests(xl, request.ID)

	return nil
}

type toolsListResponse struct {
	mcpName McpName
	tools   []mcp.Tool
	err     error
}

// listToolsFromMcp 查询单个 MCP。调用方提供一个共享的总超时上下文，
// 避免某一个下游服务按自己的超时顺序阻塞整个工具发现过程。
func listToolsFromMcp(ctx context.Context, xl xlog.Logger, mcpName McpName, mCli client.MCPClient) ([]mcp.Tool, error) {
	xl = xlog.WithChildName(mcpName, xl)

	request := mcp.ListToolsRequest{
		PaginatedRequest: mcp.PaginatedRequest{
			Request: mcp.Request{
				Method: string(mcp.MethodToolsList),
			},
		},
	}

	result, err := mCli.ListTools(ctx, request)
	if err != nil {
		xl.Errorf("Failed to list tools from MCP %s: %v", mcpName, err)
		return nil, err
	}

	xl.Debugf("Received %d tools from MCP %s", len(result.Tools), mcpName)
	return result.Tools, nil
}

// handleAllToolsRequests 并发获取下游工具，并用一次原子状态替换发布聚合结果。
func (s *Session) handleAllToolsRequests(xl xlog.Logger, requestId interface{}) {
	s.toolsListMu.Lock()
	defer s.toolsListMu.Unlock()

	xl.Info("Processing all MCP tools list requests...")
	s.toolsListComplete.Store(false)

	// 拷贝客户端引用；后续网络 I/O 不持有 Session 锁。
	s.mu.RLock()
	mcpClients := make(map[McpName]client.MCPClient, len(s.mcpClients))
	for mcpName, mcpClient := range s.mcpClients {
		mcpClients[mcpName] = mcpClient
	}
	s.mu.RUnlock()

	if len(mcpClients) == 0 {
		xl.Warn("No MCP clients available for tools list request")
		s.publishToolsList(map[McpName]map[McpToolName]mcp.Tool{}, []mcp.Tool{})
		s.sendSuccessResponse(requestId, &mcp.ListToolsResult{Tools: []mcp.Tool{}})
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), mcpToolsListTimeout)
	defer cancel()
	responses := make(chan toolsListResponse, len(mcpClients))
	for mcpName, mcpClient := range mcpClients {
		go func(name McpName, downstream client.MCPClient) {
			tools, err := listToolsFromMcp(ctx, xl, name, downstream)
			responses <- toolsListResponse{mcpName: name, tools: tools, err: err}
		}(mcpName, mcpClient)
	}

	toolsByMcp := make(map[McpName]map[McpToolName]mcp.Tool, len(mcpClients))
	for remaining := len(mcpClients); remaining > 0; remaining-- {
		select {
		case response := <-responses:
			if response.err != nil {
				xl.Warnf("Failed to fetch tools from MCP %s: %v", response.mcpName, response.err)
				continue
			}
			toolsByMcp[response.mcpName] = toolsByName(response.tools)
		case <-ctx.Done():
			xl.Warnf("Timed out after %s while fetching MCP tools", mcpToolsListTimeout)
			remaining = 0
		}
	}

	aggregatedTools := aggregateTools(toolsByMcp)
	s.publishToolsList(toolsByMcp, aggregatedTools)

	xl.Infof("Aggregated %d tools from %d MCPs", len(aggregatedTools), len(toolsByMcp))
	s.sendSuccessResponse(requestId, &mcp.ListToolsResult{Tools: aggregatedTools})
}

func toolsByName(tools []mcp.Tool) map[McpToolName]mcp.Tool {
	result := make(map[McpToolName]mcp.Tool, len(tools))
	for _, tool := range tools {
		result[tool.Name] = tool
	}
	return result
}

func aggregateTools(toolsByMcp map[McpName]map[McpToolName]mcp.Tool) []mcp.Tool {
	mcpNames := make([]string, 0, len(toolsByMcp))
	for mcpName := range toolsByMcp {
		mcpNames = append(mcpNames, mcpName)
	}
	sort.Strings(mcpNames)

	aggregatedTools := make([]mcp.Tool, 0)
	for _, mcpName := range mcpNames {
		toolNames := make([]string, 0, len(toolsByMcp[mcpName]))
		for toolName := range toolsByMcp[mcpName] {
			toolNames = append(toolNames, toolName)
		}
		sort.Strings(toolNames)

		for _, toolName := range toolNames {
			tool := toolsByMcp[mcpName][toolName]
			aggregatedTools = append(aggregatedTools, mcp.Tool{
				Name:        fmt.Sprintf("%s_%s", mcpName, tool.Name),
				Description: fmt.Sprintf("[%s] %s", mcpName, tool.Description),
				InputSchema: tool.InputSchema,
			})
		}
	}
	return aggregatedTools
}

func (s *Session) publishToolsList(toolsByMcp map[McpName]map[McpToolName]mcp.Tool, aggregatedTools []mcp.Tool) {
	s.mu.Lock()
	s.mcpToolsMap = toolsByMcp
	s.aggregatedTools = aggregatedTools
	s.toolsListComplete.Store(true)
	s.mu.Unlock()
}

// GetAllTools 获取所有聚合后的工具列表（带MCP前缀）
func (s *Session) GetAllTools() []mcp.Tool {
	s.mu.RLock()
	defer s.mu.RUnlock()

	if !s.toolsListComplete.Load() {
		return nil
	}

	// 返回副本以避免外部修改
	result := make([]mcp.Tool, len(s.aggregatedTools))
	copy(result, s.aggregatedTools)
	return result
}

// IsToolsListReady 检查工具列表是否已准备就绪
func (s *Session) IsToolsListReady() bool {
	return s.toolsListComplete.Load()
}

func (s *Session) handleMCPMethod(ctx context.Context, xl xlog.Logger, mCli client.MCPClient, mcpName McpName, method string, reqRaw json.RawMessage) (interface{}, error) {
	switch mcp.MCPMethod(method) {
	case "notifications/initialized":
		return nil, nil

	case mcp.MethodInitialize:
		s.mu.RLock()
		initializeResult := s.mcpinitializeResults[mcpName]
		s.mu.RUnlock()
		return initializeResult, nil

	case mcp.MethodPing:
		var request mcp.PingRequest
		if err := json.Unmarshal(reqRaw, &request); err != nil {
			return nil, fmt.Errorf("failed to unmarshal ping request: %w", err)
		}
		return &mcp.EmptyResult{}, mCli.Ping(ctx)

	case mcp.MethodSetLogLevel:
		var request mcp.SetLevelRequest
		if err := json.Unmarshal(reqRaw, &request); err != nil {
			return nil, fmt.Errorf("failed to unmarshal setLogLevel request: %w", err)
		}
		return &mcp.EmptyResult{}, mCli.SetLevel(ctx, request)

	case mcp.MethodResourcesList:
		var request mcp.ListResourcesRequest
		if err := json.Unmarshal(reqRaw, &request); err != nil {
			return nil, fmt.Errorf("failed to unmarshal listResources request: %w", err)
		}
		return mCli.ListResources(ctx, request)

	case mcp.MethodResourcesTemplatesList:
		var request mcp.ListResourceTemplatesRequest
		if err := json.Unmarshal(reqRaw, &request); err != nil {
			return nil, fmt.Errorf("failed to unmarshal listResourceTemplates request: %w", err)
		}
		return mCli.ListResourceTemplates(ctx, request)

	case mcp.MethodResourcesRead:
		var request mcp.ReadResourceRequest
		if err := json.Unmarshal(reqRaw, &request); err != nil {
			return nil, fmt.Errorf("failed to unmarshal readResource request: %w", err)
		}
		return mCli.ReadResource(ctx, request)

	case mcp.MethodPromptsList:
		var request mcp.ListPromptsRequest
		if err := json.Unmarshal(reqRaw, &request); err != nil {
			return nil, fmt.Errorf("failed to unmarshal listPrompts request: %w", err)
		}
		return mCli.ListPrompts(ctx, request)

	case mcp.MethodPromptsGet:
		var request mcp.GetPromptRequest
		if err := json.Unmarshal(reqRaw, &request); err != nil {
			return nil, fmt.Errorf("failed to unmarshal getPrompt request: %w", err)
		}
		return mCli.GetPrompt(ctx, request)

	case mcp.MethodToolsList:
		var request mcp.ListToolsRequest
		if err := json.Unmarshal(reqRaw, &request); err != nil {
			return nil, fmt.Errorf("failed to unmarshal listTools request: %w", err)
		}
		result, err := mCli.ListTools(ctx, request)
		if err == nil {
			s.updateToolsMap(mcpName, result)
		}
		return result, err

	case mcp.MethodToolsCall:
		var request mcp.CallToolRequest
		if err := json.Unmarshal(reqRaw, &request); err != nil {
			return nil, fmt.Errorf("failed to unmarshal callTool request: %w", err)
		}
		return mCli.CallTool(ctx, request)

	default:
		return nil, fmt.Errorf("unsupported method: %s", method)
	}
}

func (s *Session) updateToolsMap(mcpName McpName, result *mcp.ListToolsResult) {
	s.mu.Lock()
	defer s.mu.Unlock()

	if s.mcpToolsMap[mcpName] == nil {
		s.mcpToolsMap[mcpName] = make(map[McpToolName]mcp.Tool)
	}
	for _, tool := range result.Tools {
		s.mcpToolsMap[mcpName][tool.Name] = tool
	}
}

func (s *Session) IsReady() bool {
	s.mu.RLock()
	if len(s.mcpClients) == 0 {
		s.mu.RUnlock()
		return false
	}
	if len(s.mcpinitializeResults) != len(s.mcpClients) {
		s.mu.RUnlock()
		return false
	}
	clients := make([]client.MCPClient, 0, len(s.mcpClients))
	for _, mcpClient := range s.mcpClients {
		clients = append(clients, mcpClient)
	}
	s.mu.RUnlock()

	for _, mcpClient := range clients {
		ctx, cancel := context.WithTimeout(context.Background(), mcpClientReadyTimeout)
		err := mcpClient.Ping(ctx)
		cancel()
		if err != nil {
			return false
		}
	}
	return true
}

// AggregateCapabilities OR-合并所有已订阅下游 MCP 在 initialize 时声明的
// capabilities，用于网关自身对 client 的 capability 声明。只会声明至少有一个
// 下游真的支持的能力，避免 client 依据网关声明发出下游全都不支持的请求。
func (s *Session) AggregateCapabilities() mcp.ServerCapabilities {
	s.mu.RLock()
	defer s.mu.RUnlock()

	var caps mcp.ServerCapabilities
	for _, r := range s.mcpinitializeResults {
		if r == nil {
			continue
		}
		mergeCapabilities(&caps, r.Capabilities)
	}
	return caps
}

// mergeCapabilities 把 src 中声明的能力 OR 合并到 dst 中。
func mergeCapabilities(dst *mcp.ServerCapabilities, src mcp.ServerCapabilities) {
	if src.Tools != nil {
		if dst.Tools == nil {
			dst.Tools = &struct {
				ListChanged bool `json:"listChanged,omitempty"`
			}{}
		}
		if src.Tools.ListChanged {
			dst.Tools.ListChanged = true
		}
	}
	if src.Prompts != nil {
		if dst.Prompts == nil {
			dst.Prompts = &struct {
				ListChanged bool `json:"listChanged,omitempty"`
			}{}
		}
		if src.Prompts.ListChanged {
			dst.Prompts.ListChanged = true
		}
	}
	if src.Resources != nil {
		if dst.Resources == nil {
			dst.Resources = &struct {
				Subscribe   bool `json:"subscribe,omitempty"`
				ListChanged bool `json:"listChanged,omitempty"`
			}{}
		}
		if src.Resources.Subscribe {
			dst.Resources.Subscribe = true
		}
		if src.Resources.ListChanged {
			dst.Resources.ListChanged = true
		}
	}
	if src.Logging != nil && dst.Logging == nil {
		dst.Logging = &struct{}{}
	}
	if len(src.Experimental) > 0 {
		if dst.Experimental == nil {
			dst.Experimental = map[string]any{}
		}
		for k, v := range src.Experimental {
			if _, exists := dst.Experimental[k]; !exists {
				dst.Experimental[k] = v
			}
		}
	}
}
