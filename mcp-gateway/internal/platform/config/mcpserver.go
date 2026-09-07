package config

import (
	"fmt"
	"sort"
)

// MCPServerConfig 定义单个MCP服务器的配置
type MCPServerConfig struct {
	Workspace       string            `json:"workspace,omitempty"`
	URL             string            `json:"url,omitempty"`
	Command         string            `json:"command,omitempty"`
	Args            []string          `json:"args,omitempty"`
	Env             map[string]string `json:"env,omitempty"`
	GatewayProtocol string            `json:"gateway_protocol,omitempty"`

	LogConfig
	McpServiceMgrConfig
}

func (c *MCPServerConfig) GetEnvs() []string {
	list := make([]string, 0, len(c.Env))
	for key, value := range c.Env {
		list = append(list, key+"="+value)
	}
	sort.Strings(list)
	return list
}

// SafeSummary returns deployment metadata suitable for ordinary logs. It deliberately
// omits URL values, command arguments, and environment values because each may contain
// credentials or other user supplied secrets.
func (c MCPServerConfig) SafeSummary() string {
	return fmt.Sprintf("workspace=%q urlConfigured=%t commandConfigured=%t argCount=%d envCount=%d protocol=%q",
		c.Workspace, c.URL != "", c.Command != "", len(c.Args), len(c.Env), c.GatewayProtocol)
}
