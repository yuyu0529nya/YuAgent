package sessions

import (
	"os"
	"os/exec"
	"testing"

	"github.com/lucky-aeon/agentx/plugin-helper/internal/platform/config"
	"github.com/lucky-aeon/agentx/plugin-helper/internal/runtime"
)

// mockMcpServiceFileSystem 返回一个可启动的 filesystem MCP，用于 sessions 集成测试。
// 之前在同一个 service 包，拆包后保留在 sessions test 内。
func mockMcpServiceFileSystem(t *testing.T) *runtime.McpService {
	t.Helper()
	if _, err := exec.LookPath("npx"); err != nil {
		t.Skip("filesystem MCP integration test requires npx in PATH")
	}

	pwd, err := os.Getwd()
	if err != nil {
		t.Fatalf("Failed to get current working directory: %v", err)
	}

	pwd += "/testdata"
	if err := os.MkdirAll(pwd, 0755); err != nil {
		t.Fatalf("Failed to create testdata directory: %v", err)
	}
	if err := os.WriteFile(pwd+"/test.txt", []byte("Hello, World!"), 0644); err != nil {
		t.Fatalf("Failed to create fixture: %v", err)
	}
	return runtime.NewMcpService("fileSystem", config.MCPServerConfig{
		Workspace: "default",
		Command:   "npx",
		Args: []string{
			"-y",
			"@modelcontextprotocol/server-filesystem",
			pwd,
		},
		// 测试环境下把日志落到 testdata 下，避免污染仓库根目录
		LogConfig: config.LogConfig{Path: pwd},
	}, runtime.NewPortManager())
}
