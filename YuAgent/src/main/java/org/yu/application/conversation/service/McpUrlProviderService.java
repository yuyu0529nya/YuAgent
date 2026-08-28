package org.yu.application.conversation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.yu.application.container.dto.ContainerDTO;
import org.yu.application.container.service.ContainerAppService;
import org.yu.domain.container.constant.ContainerStatus;
import org.yu.domain.tool.model.ToolEntity;
import org.yu.domain.tool.service.ToolDomainService;
import org.yu.infrastructure.exception.BusinessException;
import org.yu.infrastructure.mcp_gateway.HostedMcpInstallCommandHelper;
import org.yu.infrastructure.mcp_gateway.MCPGatewayService;
import org.yu.infrastructure.utils.JsonUtils;

import java.util.Map;

/** Coordinates MCP runtime access for both user-installed tools and global tools. */
@Service
public class McpUrlProviderService {

    private static final Logger logger = LoggerFactory.getLogger(McpUrlProviderService.class);

    private final MCPGatewayService mcpGatewayService;
    private final ContainerAppService containerAppService;
    private final ToolDomainService toolDomainService;

    public McpUrlProviderService(MCPGatewayService mcpGatewayService, ContainerAppService containerAppService,
            ToolDomainService toolDomainService) {
        this.mcpGatewayService = mcpGatewayService;
        this.containerAppService = containerAppService;
        this.toolDomainService = toolDomainService;
    }

    public String getSSEUrl(String mcpServerName, String userId) {
        ToolEntity tool = resolveToolForUsage(mcpServerName, userId);
        if (isHostedTool(tool)) {
            return buildHostedToolSSEUrl(tool);
        }
        if (tool != null && tool.isGlobal()) {
            return buildReviewContainerSSEUrl(mcpServerName);
        }
        return buildUserContainerSSEUrl(mcpServerName, userId);
    }

    public String getMcpToolUrl(String mcpServerName, String userId) {
        try {
            return getSSEUrl(mcpServerName, userId);
        } catch (Exception e) {
            logger.error("Failed to resolve MCP tool URL: userId={}, tool={}", userId, mcpServerName, e);
            throw new BusinessException("无法连接工具: " + mcpServerName + " - " + e.getMessage());
        }
    }

    private ToolEntity resolveToolForUsage(String mcpServerName, String userId) {
        return toolDomainService.getToolByServerNameForUsage(mcpServerName, userId);
    }

    private boolean isHostedTool(ToolEntity tool) {
        return tool != null && tool.getInstallCommand() != null
                && HostedMcpInstallCommandHelper.isHostedConfig(tool.getInstallCommand());
    }

    private String buildHostedToolSSEUrl(ToolEntity tool) {
        try {
            HostedMcpInstallCommandHelper.NormalizedInstallCommand normalizedInstallCommand = HostedMcpInstallCommandHelper
                    .normalizeInstallCommand(tool.getInstallCommand(), tool.getMcpServerName(), tool.getName());
            String installCommandJson = convertInstallCommand(normalizedInstallCommand.installCommand());

            logger.info("Preparing hosted MCP connection via shared gateway: tool={}, server={}", tool.getId(),
                    normalizedInstallCommand.serverName());
            boolean deploySuccess = mcpGatewayService.deployTool(installCommandJson);
            if (!deploySuccess) {
                throw new BusinessException("托管MCP共享网关部署失败");
            }

            String sseUrl = mcpGatewayService.buildGlobalSSEUrl(normalizedInstallCommand.serverName());
            logger.info("Hosted MCP connection ready via shared gateway: tool={}, url={}", tool.getId(),
                    maskSensitiveInfo(sseUrl));
            return sseUrl;
        } catch (Exception e) {
            logger.error("Failed to build hosted MCP URL: tool={}", tool != null ? tool.getId() : null, e);
            throw new BusinessException("无法连接托管MCP工具: " + e.getMessage(), e);
        }
    }

    private String buildUserContainerSSEUrl(String mcpServerName, String userId) {
        try {
            logger.info("Preparing user container MCP connection: userId={}, tool={}", userId, mcpServerName);

            ContainerDTO containerInfo = ensureUserContainerReady(userId);
            deployTool(containerInfo, mcpServerName, userId);

            String sseUrl = mcpGatewayService.buildUserContainerUrl(mcpServerName, containerInfo.getIpAddress(),
                    resolveContainerAccessPort(containerInfo));
            logger.info("User container MCP connection ready: userId={}, url={}", userId, maskSensitiveInfo(sseUrl));
            return sseUrl;
        } catch (Exception e) {
            logger.error("Failed to build user container MCP URL: userId={}, tool={}", userId, mcpServerName, e);
            throw new BusinessException("无法连接用户工具: " + e.getMessage(), e);
        }
    }

    private String buildReviewContainerSSEUrl(String mcpServerName) {
        try {
            logger.info("Preparing review container MCP connection: tool={}", mcpServerName);
            ContainerDTO containerInfo = ensureReviewContainerReady();
            String sseUrl = mcpGatewayService.buildUserContainerUrl(mcpServerName, containerInfo.getIpAddress(),
                    resolveContainerAccessPort(containerInfo));
            logger.info("Review container MCP connection ready: tool={}, url={}", mcpServerName,
                    maskSensitiveInfo(sseUrl));
            return sseUrl;
        } catch (Exception e) {
            logger.error("Failed to build review container MCP URL: tool={}", mcpServerName, e);
            throw new BusinessException("无法连接全局工具: " + e.getMessage(), e);
        }
    }

    private ContainerDTO ensureUserContainerReady(String userId) {
        try {
            ContainerDTO userContainer = containerAppService.getUserContainer(userId);
            if (!isContainerHealthy(userContainer)) {
                throw new BusinessException("用户容器准备失败，状态异常: " + userContainer.getStatus());
            }
            return userContainer;
        } catch (Exception e) {
            logger.error("Failed to prepare user container: userId={}", userId, e);
            throw new BusinessException("用户容器准备失败: " + e.getMessage(), e);
        }
    }

    private boolean isContainerHealthy(ContainerDTO container) {
        if (container == null) {
            return false;
        }

        boolean isRunning = ContainerStatus.RUNNING.equals(container.getStatus());
        boolean hasNetworkInfo = container.getIpAddress() != null && resolveContainerAccessPort(container) != null;
        boolean hasDockerContainerId = container.getDockerContainerId() != null;
        boolean basicHealthy = isRunning && hasNetworkInfo && hasDockerContainerId;

        if (!basicHealthy) {
            logger.warn("Container health check failed: containerId={}, running={}, networkInfo={}, dockerId={}",
                    container.getId(), isRunning, hasNetworkInfo, hasDockerContainerId);
        }
        return basicHealthy;
    }

    private void deployTool(ContainerDTO container, String toolName, String userId) {
        try {
            ToolEntity tool = toolDomainService.getToolByServerNameForUsage(toolName, userId);
            if (tool == null) {
                throw new BusinessException("无法找到工具定义: " + toolName);
            }

            HostedMcpInstallCommandHelper.NormalizedInstallCommand normalizedInstallCommand = HostedMcpInstallCommandHelper
                    .normalizeInstallCommand(tool.getInstallCommand(), tool.getMcpServerName(), tool.getName());
            tool.setInstallCommand(normalizedInstallCommand.installCommand());

            String installCommandJson = convertInstallCommand(tool.getInstallCommand());
            boolean deploySuccess = mcpGatewayService.deployTool(installCommandJson, container.getIpAddress(),
                    resolveContainerAccessPort(container));
            if (!deploySuccess) {
                throw new BusinessException("MCP 容器内部部署失败");
            }

            logger.debug("Tool {} deployed to user container", toolName);
        } catch (Exception e) {
            logger.warn("Failed to deploy tool into user container: tool={}, error={}", toolName, e.getMessage());
            throw new BusinessException("部署用户容器工具失败: " + e.getMessage(), e);
        }
    }

    private String convertInstallCommand(Map<String, Object> installCommand) {
        try {
            return JsonUtils.toJsonString(installCommand);
        } catch (Exception e) {
            throw new BusinessException("转换安装命令失败: " + e.getMessage(), e);
        }
    }

    private ContainerDTO ensureReviewContainerReady() {
        try {
            ContainerDTO reviewContainer = containerAppService.getOrCreateReviewContainer();
            if (!isContainerHealthy(reviewContainer)) {
                throw new BusinessException("审核容器准备失败，状态异常: " + reviewContainer.getStatus());
            }
            return reviewContainer;
        } catch (Exception e) {
            logger.error("Failed to prepare review container", e);
            throw new BusinessException("审核容器准备失败: " + e.getMessage(), e);
        }
    }

    private String maskSensitiveInfo(String url) {
        if (url == null) {
            return null;
        }
        return url.replaceAll("api_key=[^&]*", "api_key=***");
    }

    private Integer resolveContainerAccessPort(ContainerDTO container) {
        if (container == null || container.getIpAddress() == null) {
            return null;
        }
        if (isLocalAddress(container.getIpAddress())) {
            return container.getExternalPort();
        }
        return container.getInternalPort();
    }

    private boolean isLocalAddress(String ipAddress) {
        return "localhost".equalsIgnoreCase(ipAddress) || "host.docker.internal".equalsIgnoreCase(ipAddress)
                || "127.0.0.1".equals(ipAddress) || "::1".equals(ipAddress) || "0:0:0:0:0:0:0:1".equals(ipAddress);
    }
}
