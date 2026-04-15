package config

// MCPServerConfig 定义单个MCP服务器的配置
type MCPServerConfig struct {
	Workspace string            `json:"workspace,omitempty"`
	URL       string            `json:"url,omitempty"`
	BaseURL   string            `json:"baseUrl,omitempty"`
	Type      string            `json:"type,omitempty"`
	Command   string            `json:"command,omitempty"`
	Args      []string          `json:"args,omitempty"`
	Env       map[string]string `json:"env,omitempty"`
	Headers   map[string]string `json:"headers,omitempty"`

	LogConfig
	McpServiceMgrConfig
}

func (c *MCPServerConfig) GetEnvs() []string {
	list := make([]string, 0, len(c.Env))
	for s := range c.Env {
		list = append(list, s)
	}
	return list
}

func (c *MCPServerConfig) ResolveURL() string {
	if c.URL != "" {
		return c.URL
	}
	return c.BaseURL
}
