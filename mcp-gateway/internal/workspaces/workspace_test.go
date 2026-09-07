package workspaces

import (
	"testing"

	"github.com/lucky-aeon/agentx/plugin-helper/internal/platform/config"
	"github.com/lucky-aeon/agentx/plugin-helper/internal/platform/xlog"
	"github.com/lucky-aeon/agentx/plugin-helper/internal/sessions"
)

func TestAddMcpService_ReusesIdenticalRunningConfiguration(t *testing.T) {
	workspace := newTestWorkspace()
	logger := xlog.NewLogger("workspace-test")
	serviceConfig := config.MCPServerConfig{URL: "https://mcp.example.test/sse", Env: map[string]string{"TOKEN": "same"}}

	result, err := workspace.AddMcpService(logger, "weather", serviceConfig)
	if err != nil || result != AddMcpServiceResultDeployed {
		t.Fatalf("initial deployment = %q, %v", result, err)
	}

	result, err = workspace.AddMcpService(logger, "weather", serviceConfig)
	if err != nil || result != AddMcpServiceResultExisted {
		t.Fatalf("identical deployment = %q, %v", result, err)
	}
	service, err := workspace.GetMcpService("weather")
	if err != nil || service.GetUrl() != serviceConfig.URL {
		t.Fatalf("reused service = %q, %v", service.GetUrl(), err)
	}
}

func TestAddMcpService_ReplacesRunningServiceWhenConfigurationChanges(t *testing.T) {
	workspace := newTestWorkspace()
	logger := xlog.NewLogger("workspace-test")

	first := config.MCPServerConfig{URL: "https://mcp.example.test/old", Env: map[string]string{"TOKEN": "old"}}
	second := config.MCPServerConfig{URL: "https://mcp.example.test/new", Env: map[string]string{"TOKEN": "new"}}
	if _, err := workspace.AddMcpService(logger, "weather", first); err != nil {
		t.Fatal(err)
	}

	result, err := workspace.AddMcpService(logger, "weather", second)
	if err != nil || result != AddMcpServiceResultReplaced {
		t.Fatalf("changed deployment = %q, %v", result, err)
	}
	service, err := workspace.GetMcpService("weather")
	if err != nil {
		t.Fatal(err)
	}
	if service.GetUrl() != second.URL {
		t.Fatalf("service URL = %q, want %q", service.GetUrl(), second.URL)
	}
	if saved, ok := workspace.cfg.GetMcpServerCfg("weather"); !ok || saved.URL != second.URL || saved.Env["TOKEN"] != "new" {
		t.Fatalf("saved config was not updated: %+v, %v", saved, ok)
	}
}

func TestAddMcpService_ReplacesWhenRetryPolicyChanges(t *testing.T) {
	workspace := newTestWorkspace()
	logger := xlog.NewLogger("workspace-test")

	first := config.MCPServerConfig{URL: "https://mcp.example.test/retry", McpServiceMgrConfig: config.McpServiceMgrConfig{McpServiceRetryCount: 1}}
	second := config.MCPServerConfig{URL: "https://mcp.example.test/retry", McpServiceMgrConfig: config.McpServiceMgrConfig{McpServiceRetryCount: 5}}
	if _, err := workspace.AddMcpService(logger, "retry", first); err != nil {
		t.Fatal(err)
	}

	result, err := workspace.AddMcpService(logger, "retry", second)
	if err != nil || result != AddMcpServiceResultReplaced {
		t.Fatalf("retry policy update = %q, %v", result, err)
	}
}

func newTestWorkspace() *WorkSpace {
	return NewWorkSpace("default", config.WorkspaceConfig{
		Servers: make(map[string]config.MCPServerConfig),
	}, nil, sessions.CleanupConfig{})
}
