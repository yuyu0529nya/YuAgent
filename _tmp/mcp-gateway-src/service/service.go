package service

import (
	"context"
	"fmt"
	"crypto/tls"
	"net/http"
	"net/http/httputil"
	"net/url"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/lucky-aeon/agentx/plugin-helper/bridge"
	"github.com/lucky-aeon/agentx/plugin-helper/config"
	"github.com/lucky-aeon/agentx/plugin-helper/xlog"
	"github.com/mark3labs/mcp-go/client/transport"
)

type CmdStatus string

const (
	Starting CmdStatus = "starting"
	Running  CmdStatus = "Running"
	Stopping CmdStatus = "Stopping"
	Stopped  CmdStatus = "Stopped"
	Failed   CmdStatus = "Failed"
)

type ExportMcpService interface {
	GetUrl() string
	GetSSEUrl() string
	GetMessageUrl() string
	GetStatus() CmdStatus
	SendMessage(message string) error
	Info() McpServiceInfo
	GetHealthStatus() map[string]interface{}
}

type McpService struct {
	Name    string
	Config  config.MCPServerConfig
	LogFile *os.File
	logger  xlog.Logger
	Port    int

	portMgr PortManagerI

	Status     CmdStatus
	RetryCount int
	RetryMax   int

	bridge           *bridge.StdioToSSEBridge
	httpStreamBridge *bridge.HTTPStreamToSSEBridge
	proxyServer      *http.Server
	proxyPort        int

	LastError      string
	FailureReason  string
	DeployedAt     time.Time
	LastStartedAt  time.Time
	LastStoppedAt  time.Time
	HealthCheckURL string

	mutex sync.RWMutex
}

func NewMcpService(name string, cfg config.MCPServerConfig, portMgr PortManagerI) *McpService {
	logger := xlog.NewLogger(fmt.Sprintf("[MCP-%s]", name))
	return &McpService{
		Name:       name,
		Config:     cfg,
		portMgr:    portMgr,
		Status:     Stopped,
		logger:     logger,
		RetryMax:   cfg.McpServiceMgrConfig.GetMcpServiceRetryCount(),
		DeployedAt: time.Now(),
	}
}

func (s *McpService) IsSSE() bool {
	if s.Config.Command == "" && s.Config.ResolveURL() != "" && !s.isHostedStreamableHTTP() {
		s.Status = Running
		return true
	}
	return false
}

func (s *McpService) Stop(logger xlog.Logger) (err error) {
	if s.IsSSE() {
		return nil
	}

	s.mutex.Lock()
	defer s.mutex.Unlock()

	if s.Status != Running && s.Status != Starting {
		return nil
	}

	logger.Infof("Stopping service %s", s.Name)
	s.Status = Stopping
	s.LastStoppedAt = time.Now()
	defer func() {
		if s.Status == Stopping {
			s.Status = Stopped
		}
		s.bridge = nil
		s.httpStreamBridge = nil
		s.proxyServer = nil
		s.proxyPort = 0
	}()

	if s.bridge != nil {
		if closeErr := s.bridge.Close(); closeErr != nil {
			logger.Errorf("Failed to stop stdio-sse bridge: %v", closeErr)
		}
	}
	if s.httpStreamBridge != nil {
		if closeErr := s.httpStreamBridge.Close(); closeErr != nil {
			logger.Errorf("Failed to stop http-stream-sse bridge: %v", closeErr)
		}
	}
	if s.proxyServer != nil {
		if closeErr := s.proxyServer.Shutdown(context.Background()); closeErr != nil {
			logger.Errorf("Failed to stop upstream proxy server: %v", closeErr)
		}
	}
	if s.LogFile != nil {
		err = s.LogFile.Close()
		if err != nil {
			logger.Errorf("Failed to close log file: %v", err)
		}
		s.LogFile = nil
	}
	return err
}

func (s *McpService) Start(logger xlog.Logger) error {
	if s.IsSSE() {
		logger.Infof("Service %s is hosted SSE and does not require a local process", s.Name)
		return nil
	}

	s.mutex.Lock()
	defer s.mutex.Unlock()

	if s.Status == Running {
		return fmt.Errorf("service %s is already running", s.Name)
	}
	if s.Status == Failed {
		return fmt.Errorf("service %s is failed and cannot be started", s.Name)
	}

	s.Status = Starting
	s.LastStartedAt = time.Now()
	s.LastError = ""
	s.FailureReason = ""

	if s.Port == 0 {
		s.Port = s.portMgr.GetNextAvailablePort()
		logger.Infof("Assigned bridge port: %d", s.Port)
	}
	if s.isHostedStreamableHTTP() && s.proxyPort == 0 {
		s.proxyPort = s.portMgr.GetNextAvailablePort()
		logger.Infof("Assigned upstream proxy port: %d", s.proxyPort)
	}

	logFile, err := xlog.CreateLogFile(s.Config.LogConfig.Path, s.Name+".log")
	if err != nil {
		s.LastError = fmt.Sprintf("failed to create log file: %v", err)
		s.FailureReason = "Log file creation failed"
		s.Status = Failed
		return fmt.Errorf("failed to create log file: %v", err)
	}
	logger.Infof("Created log file: %s", logFile.Name())
	s.LogFile = logFile

	ctx, cancel := context.WithTimeout(context.Background(), 300*time.Second)
	defer cancel()

	var startFn func(string) error

	if s.isHostedStreamableHTTP() {
		upstreamURL, proxyServer, err := s.startUpstreamProxyServer(logger)
		if err != nil {
			_ = logFile.Close()
			s.LastError = fmt.Sprintf("failed to start upstream proxy: %v", err)
			s.FailureReason = "Upstream proxy creation failed"
			s.Status = Failed
			return fmt.Errorf("failed to start upstream proxy: %w", err)
		}
		s.proxyServer = proxyServer

		httpBridge, err := bridge.NewHTTPStreamToSSEBridge(ctx, upstreamURL, s.Name)
		if err != nil {
			_ = logFile.Close()
			_ = proxyServer.Shutdown(context.Background())
			s.LastError = fmt.Sprintf("failed to create http-stream-sse bridge: %v", err)
			s.FailureReason = "HTTP stream bridge creation failed"
			s.Status = Failed
			return fmt.Errorf("failed to create http-stream-sse bridge: %w", err)
		}
		s.httpStreamBridge = httpBridge
		startFn = httpBridge.Start
	} else {
		stdioBridge, err := bridge.NewStdioToSSEBridge(ctx,
			transport.NewStdio(s.Config.Command, s.Config.GetEnvs(), s.Config.Args...), s.Name)
		if err != nil {
			_ = logFile.Close()
			s.LastError = fmt.Sprintf("failed to create stdio-sse bridge: %v", err)
			s.FailureReason = "Bridge creation failed"
			s.Status = Failed
			return fmt.Errorf("failed to create stdio-sse bridge: %w", err)
		}
		s.bridge = stdioBridge
		startFn = stdioBridge.Start
	}

	startupChan := make(chan error, 1)
	go func() {
		defer close(startupChan)
		logger.Infof("Starting bridge server on port %d", s.Port)
		if err := startFn(fmt.Sprintf("0.0.0.0:%d", s.Port)); err != nil {
			logger.Errorf("Bridge server failed: %v", err)
			startupChan <- err
		}
	}()

	startupTimeout := time.NewTimer(3 * time.Second)
	defer startupTimeout.Stop()

	select {
	case err := <-startupChan:
		if err != nil {
			_ = logFile.Close()
			s.LastError = err.Error()
			s.FailureReason = "Bridge server startup failed"
			s.Status = Failed
			return fmt.Errorf("bridge server startup failed: %w", err)
		}
	case <-startupTimeout.C:
		logger.Infof("Bridge startup timed out, checking whether the bridge is responsive")
		if err := s.pingActiveBridge(context.Background()); err != nil {
			_ = logFile.Close()
			s.LastError = fmt.Sprintf("Bridge health check failed: %v", err)
			s.FailureReason = "Bridge server not responding"
			s.Status = Failed
			return fmt.Errorf("bridge server not responding: %w", err)
		}
	}

	s.Status = Running
	s.RetryCount = s.RetryMax
	s.HealthCheckURL = fmt.Sprintf("http://0.0.0.0:%d/health", s.Port)

	logger.Infof("Started service %s on bridge port %d", s.Name, s.Port)
	return nil
}

func (s *McpService) Restart(logger xlog.Logger) {
	if s.IsSSE() {
		logger.Infof("Service %s is hosted SSE and does not require a restart", s.Name)
		return
	}

	s.mutex.Lock()
	if s.RetryCount <= 0 {
		logger.Warnf("No retry count left for %s, marking as failed", s.Name)
		s.Status = Failed
		s.FailureReason = "Max retry count reached"
		s.LastError = "Service failed after maximum retry attempts"
		s.mutex.Unlock()
		return
	}

	s.RetryCount--
	currentAttempt := s.RetryMax - s.RetryCount
	retryCount := s.RetryCount
	logger.Infof("Restarting %s (attempt %d/%d)", s.Name, currentAttempt, s.RetryMax)
	s.mutex.Unlock()

	if err := s.Stop(logger); err != nil {
		logger.Errorf("Failed to stop service %s during restart: %v", s.Name, err)
	}

	if err := s.Start(logger); err != nil {
		logger.Errorf("Failed to restart %s: %v", s.Name, err)

		s.mutex.Lock()
		s.LastError = fmt.Sprintf("Failed to restart: %v", err)
		if retryCount > 0 {
			s.FailureReason = fmt.Sprintf("Restart attempt %d/%d failed", currentAttempt, s.RetryMax)
			s.mutex.Unlock()
			time.AfterFunc(5*time.Second, func() {
				s.Restart(logger)
			})
		} else {
			s.Status = Failed
			s.FailureReason = "All restart attempts failed"
			s.mutex.Unlock()
		}
	}
}

func (s *McpService) setConfig(cfg config.MCPServerConfig) error {
	if s.Status != Stopped {
		return fmt.Errorf("service %s is running, cannot set config", s.Name)
	}
	s.Config = cfg
	return nil
}

func (s *McpService) isHostedService() bool {
	return s.Config.Command == "" && s.Config.ResolveURL() != ""
}

func (s *McpService) isHostedStreamableHTTP() bool {
	return strings.EqualFold(s.Config.Type, "streamableHttp") && s.isHostedService()
}

func (s *McpService) resolveHostedSSEURL() string {
	return s.Config.ResolveURL()
}

func (s *McpService) resolveHostedMessageURL() string {
	baseURL := s.Config.ResolveURL()
	if baseURL == "" {
		return ""
	}

	parsed, err := url.Parse(baseURL)
	if err != nil {
		return strings.Replace(baseURL, "/sse", "/message", 1)
	}

	if strings.HasSuffix(parsed.Path, "/sse") {
		parsed.Path = strings.TrimSuffix(parsed.Path, "/sse") + "/message"
	}

	return parsed.String()
}

func (s *McpService) GetUrl() string {
	if s.GetStatus() != Running {
		return ""
	}
	if s.bridge != nil || s.httpStreamBridge != nil {
		return "http://127.0.0.1:" + strconv.Itoa(s.Port)
	}
	if s.Config.ResolveURL() != "" {
		return s.Config.ResolveURL()
	}
	return ""
}

func (s *McpService) GetSSEUrl() string {
	if s.GetStatus() != Running {
		return ""
	}
	if s.bridge != nil {
		sseURL, _ := s.bridge.CompleteSseEndpoint()
		return s.GetUrl() + sseURL
	}
	if s.httpStreamBridge != nil {
		sseURL, _ := s.httpStreamBridge.CompleteSseEndpoint()
		return s.GetUrl() + sseURL
	}
	if s.isHostedService() {
		return s.resolveHostedSSEURL()
	}
	return ""
}

func (s *McpService) GetMessageUrl() string {
	if s.GetStatus() != Running {
		return ""
	}
	if s.bridge != nil {
		messageURL, _ := s.bridge.CompleteMessageEndpoint()
		return s.GetUrl() + messageURL
	}
	if s.httpStreamBridge != nil {
		messageURL, _ := s.httpStreamBridge.CompleteMessageEndpoint()
		return s.GetUrl() + messageURL
	}
	if s.isHostedService() {
		return s.resolveHostedMessageURL()
	}
	return ""
}

func (s *McpService) GetPort() int {
	return s.Port
}

func (s *McpService) GetStatus() CmdStatus {
	s.mutex.RLock()
	defer s.mutex.RUnlock()
	return s.Status
}

func (s *McpService) SendMessage(message string) error {
	resp, err := http.Post(s.GetMessageUrl(), "application/json", strings.NewReader(message))
	if err != nil {
		return fmt.Errorf("failed to send message: %v", err)
	}
	defer func() {
		if err := resp.Body.Close(); err != nil {
			s.logger.Errorf("Failed to close response body: %v", err)
		}
	}()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("failed to send message, status code: %d", resp.StatusCode)
	}
	return nil
}

func (s *McpService) pingActiveBridge(ctx context.Context) error {
	if s.httpStreamBridge != nil {
		return s.httpStreamBridge.Ping(ctx)
	}
	if s.bridge != nil {
		return s.bridge.Ping(ctx)
	}
	return fmt.Errorf("no active bridge")
}

func (s *McpService) startUpstreamProxyServer(logger xlog.Logger) (string, *http.Server, error) {
	target, err := url.Parse(s.Config.ResolveURL())
	if err != nil {
		return "", nil, fmt.Errorf("invalid upstream URL: %w", err)
	}

	proxy := httputil.NewSingleHostReverseProxy(target)
	director := proxy.Director
	proxy.Director = func(req *http.Request) {
		director(req)
		for k, v := range s.Config.Headers {
			req.Header.Set(k, v)
		}
		req.Host = target.Host
	}
	proxy.Transport = &http.Transport{
		Proxy:                 http.ProxyFromEnvironment,
		ForceAttemptHTTP2:     false,
		MaxIdleConns:          100,
		MaxIdleConnsPerHost:   20,
		IdleConnTimeout:       90 * time.Second,
		TLSHandshakeTimeout:   30 * time.Second,
		ResponseHeaderTimeout: 30 * time.Second,
		ExpectContinueTimeout: 5 * time.Second,
		TLSClientConfig: &tls.Config{
			MinVersion: tls.VersionTLS12,
		},
	}
	proxy.ErrorHandler = func(rw http.ResponseWriter, req *http.Request, err error) {
		logger.Errorf("Upstream proxy request failed: %v", err)
		rw.WriteHeader(http.StatusBadGateway)
		_, _ = rw.Write([]byte(err.Error()))
	}

	server := &http.Server{
		Addr:              fmt.Sprintf("0.0.0.0:%d", s.proxyPort),
		Handler:           proxy,
		ReadHeaderTimeout: 10 * time.Second,
		IdleTimeout:       120 * time.Second,
	}

	go func() {
		logger.Infof("Starting upstream proxy server on port %d", s.proxyPort)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			logger.Errorf("Upstream proxy server failed: %v", err)
		}
	}()

	time.Sleep(500 * time.Millisecond)
	return fmt.Sprintf("http://127.0.0.1:%d", s.proxyPort), server, nil
}

type McpServiceInfo struct {
	Name          string                 `json:"name"`
	Status        CmdStatus              `json:"status"`
	Config        config.MCPServerConfig `json:"config"`
	Port          int                    `json:"port"`
	LastError     string                 `json:"last_error,omitempty"`
	FailureReason string                 `json:"failure_reason,omitempty"`
	DeployedAt    time.Time              `json:"deployed_at"`
	LastStartedAt time.Time              `json:"last_started_at,omitempty"`
	LastStoppedAt time.Time              `json:"last_stopped_at,omitempty"`
	RetryCount    int                    `json:"retry_count"`
	RetryMax      int                    `json:"retry_max"`
	URLs          ServiceURLs            `json:"urls"`
}

type ServiceURLs struct {
	BaseURL    string `json:"base_url,omitempty"`
	SSEUrl     string `json:"sse_url,omitempty"`
	MessageUrl string `json:"message_url,omitempty"`
}

func (s *McpService) Info() McpServiceInfo {
	s.mutex.RLock()
	defer s.mutex.RUnlock()
	return McpServiceInfo{
		Name:          s.Name,
		Status:        s.Status,
		Config:        s.Config,
		Port:          s.Port,
		LastError:     s.LastError,
		FailureReason: s.FailureReason,
		DeployedAt:    s.DeployedAt,
		LastStartedAt: s.LastStartedAt,
		LastStoppedAt: s.LastStoppedAt,
		RetryCount:    s.RetryCount,
		RetryMax:      s.RetryMax,
		URLs: ServiceURLs{
			BaseURL:    s.GetUrl(),
			SSEUrl:     s.GetSSEUrl(),
			MessageUrl: s.GetMessageUrl(),
		},
	}
}

func (s *McpService) GetHealthStatus() map[string]interface{} {
	s.mutex.RLock()
	defer s.mutex.RUnlock()

	health := map[string]interface{}{
		"name":            s.Name,
		"status":          s.Status,
		"healthy":         s.Status == Running,
		"port":            s.Port,
		"deployed_at":     s.DeployedAt,
		"last_started_at": s.LastStartedAt,
		"last_stopped_at": s.LastStoppedAt,
		"retry_count":     s.RetryCount,
		"retry_max":       s.RetryMax,
	}

	if s.LastError != "" {
		health["last_error"] = s.LastError
	}
	if s.FailureReason != "" {
		health["failure_reason"] = s.FailureReason
	}
	if s.HealthCheckURL != "" {
		health["health_check_url"] = s.HealthCheckURL
	}
	if s.Status == Running && !s.LastStartedAt.IsZero() {
		health["uptime_seconds"] = time.Since(s.LastStartedAt).Seconds()
	}

	return health
}
