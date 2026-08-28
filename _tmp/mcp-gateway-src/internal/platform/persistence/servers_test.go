package persistence

import (
	"strings"
	"testing"

	"github.com/lucky-aeon/agentx/plugin-helper/internal/platform/config"
)

func TestServerLogSummaryDoesNotExposeSensitiveConfigValues(t *testing.T) {
	server := config.MCPServerConfig{
		Workspace:       "default",
		URL:             "https://mcp.example.com/sse?token=url-secret",
		Command:         "npx",
		Args:            []string{"--api-key", "argument-secret"},
		Env:             map[string]string{"API_KEY": "environment-secret"},
		GatewayProtocol: "streamhttp",
	}

	summary := serverLogSummary(server)
	for _, secret := range []string{"url-secret", "argument-secret", "environment-secret"} {
		if strings.Contains(summary, secret) {
			t.Fatalf("summary exposed secret %q: %s", secret, summary)
		}
	}
	for _, expected := range []string{"urlConfigured=true", "commandConfigured=true", "argCount=2", "envCount=1"} {
		if !strings.Contains(summary, expected) {
			t.Fatalf("summary missing %q: %s", expected, summary)
		}
	}
}
