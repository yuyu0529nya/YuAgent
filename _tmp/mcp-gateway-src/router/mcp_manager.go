package router

import (
	"fmt"
	"net/http"

	"github.com/labstack/echo/v4"
	"github.com/lucky-aeon/agentx/plugin-helper/config"
	"github.com/lucky-aeon/agentx/plugin-helper/service"
	"github.com/lucky-aeon/agentx/plugin-helper/types"
	"github.com/lucky-aeon/agentx/plugin-helper/utils"
	"github.com/lucky-aeon/agentx/plugin-helper/xlog"
)

// GET ALL MCP SERVICES
func (m *ServerManager) handleGetAllServices(c echo.Context) error {
	xl := xlog.NewLogger("GET-SERVICES")
	xl.Infof("Get all services")
	workspace := utils.GetWorkspace(c, service.DefaultWorkspace)
	mcpServices := m.mcpServiceMgr.GetMcpServices(xl, service.NameArg{
		Workspace: workspace,
	})
	var serviceInfos []service.McpServiceInfo
	for _, instance := range mcpServices {
		serviceInfos = append(serviceInfos, instance.Info())
	}
	return c.JSON(http.StatusOK, serviceInfos)
}

// DeployServer deploys a single MCP service config.
func (m *ServerManager) DeployServer(name string, cfg config.MCPServerConfig) (service.AddMcpServiceResult, error) {
	m.Lock()
	defer m.Unlock()

	logger := xlog.NewLogger("DEPLOY")
	resolvedURL := cfg.ResolveURL()

	if cfg.Command == "" && resolvedURL == "" {
		return "", fmt.Errorf("service config must include URL/BaseURL or Command")
	}

	if cfg.Command != "" && resolvedURL != "" {
		return "", fmt.Errorf("service config cannot include both URL/BaseURL and Command")
	}

	if cfg.URL == "" && resolvedURL != "" {
		cfg.URL = resolvedURL
	}

	if cfg.Workspace == "" {
		cfg.Workspace = service.DefaultWorkspace
	}

	return m.mcpServiceMgr.DeployServer(logger, service.NameArg{
		Server:    name,
		Workspace: cfg.Workspace,
	}, cfg)
}

// handleDeploy processes a batch deploy request.
func (m *ServerManager) handleDeploy(c echo.Context) error {
	xl := xlog.NewLogger("DEPLOY-REQ")
	xl.Infof("Deploy request: %v", c.Request().Body)
	var req types.DeployRequest
	if err := c.Bind(&req); err != nil {
		return c.JSON(http.StatusBadRequest, map[string]string{"error": err.Error()})
	}
	xl.Infof("Deploy request: %v", req)
	workspace := utils.GetWorkspace(c, service.DefaultWorkspace)

	response := types.DeployResponse{
		Success: true,
		Results: make(map[string]types.ServiceDeployResult),
		Summary: types.DeploymentSummary{
			Total: len(req.MCPServers),
		},
	}

	for name, cfg := range req.MCPServers {
		xl.Infof("Deploying %s: %v", name, cfg)
		if workspace != "" {
			cfg.Workspace = workspace
		} else if cfg.Workspace == "" {
			cfg.Workspace = service.DefaultWorkspace
		}

		result, err := m.DeployServer(name, cfg)
		serviceResult := types.ServiceDeployResult{Name: name}

		if err != nil {
			xl.Errorf("Failed to deploy %s: %v", name, err)
			serviceResult.Status = types.ServiceDeployStatusFailed
			serviceResult.Error = err.Error()
			serviceResult.Message = fmt.Sprintf("部署失败: %v", err)
			response.Summary.Failed++
			response.Success = false
		} else {
			switch result {
			case service.AddMcpServiceResultDeployed:
				serviceResult.Status = types.ServiceDeployStatusDeployed
				serviceResult.Message = "服务部署成功"
				response.Summary.Deployed++
			case service.AddMcpServiceResultExisted:
				serviceResult.Status = types.ServiceDeployStatusExisted
				serviceResult.Message = "服务已存在且正在运行"
				response.Summary.Existed++
			case service.AddMcpServiceResultReplaced:
				serviceResult.Status = types.ServiceDeployStatusReplaced
				serviceResult.Message = "服务已替换（原服务已停止或失败）"
				response.Summary.Replaced++
			}
		}

		response.Results[name] = serviceResult
	}

	if response.Success {
		response.Message = fmt.Sprintf("部署完成: %d个服务总计，%d个新部署，%d个已存在，%d个已替换，%d个失败",
			response.Summary.Total, response.Summary.Deployed,
			response.Summary.Existed, response.Summary.Replaced, response.Summary.Failed)
	} else {
		response.Message = fmt.Sprintf("部署完成但存在失败: %d个服务总计，%d个新部署，%d个已存在，%d个已替换，%d个失败",
			response.Summary.Total, response.Summary.Deployed,
			response.Summary.Existed, response.Summary.Replaced, response.Summary.Failed)
	}

	xl.Infof("Deployment completed: %s", response.Message)

	statusCode := http.StatusOK
	if response.Summary.Failed > 0 {
		statusCode = http.StatusPartialContent
	}

	return c.JSON(statusCode, response)
}

// handleDeleteMcpService deletes a single service.
func (m *ServerManager) handleDeleteMcpService(c echo.Context) error {
	xl := xlog.NewLogger("DELETE-SVC")
	xl.Infof("Delete request: %v", c.Request().Body)
	name := c.QueryParam("name")
	workspace := utils.GetWorkspace(c, service.DefaultWorkspace)
	if err := m.mcpServiceMgr.DeleteServer(xl, service.NameArg{
		Server:    name,
		Workspace: workspace,
	}); err != nil {
		return c.JSON(http.StatusInternalServerError, map[string]string{"error": err.Error()})
	}
	return c.JSON(http.StatusOK, map[string]string{"status": "success"})
}

// handleGetServiceHealth returns service health information.
func (m *ServerManager) handleGetServiceHealth(c echo.Context) error {
	xl := xlog.NewLogger("GET-SERVICE-HEALTH")
	serviceName := c.Param("name")
	workspace := utils.GetWorkspace(c, service.DefaultWorkspace)

	xl.Infof("Get service health for %s in workspace %s", serviceName, workspace)

	mcpService, err := m.mcpServiceMgr.GetMcpService(xl, service.NameArg{
		Server:    serviceName,
		Workspace: workspace,
	})
	if err != nil {
		return c.JSON(http.StatusNotFound, map[string]string{"error": fmt.Sprintf("Service %s not found: %v", serviceName, err)})
	}

	health := mcpService.GetHealthStatus()
	return c.JSON(http.StatusOK, health)
}
