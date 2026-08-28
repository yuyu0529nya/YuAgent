package org.yu.application.tool.service.state.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yu.application.container.service.ReviewContainerService;
import org.yu.application.tool.service.state.AppToolStateProcessor;
import org.yu.domain.tool.constant.ToolStatus;
import org.yu.domain.tool.model.ToolEntity;
import org.yu.domain.tool.model.config.ToolDefinition;
import org.yu.infrastructure.exception.BusinessException;
import org.yu.infrastructure.mcp_gateway.HostedMcpInstallCommandHelper;
import org.yu.infrastructure.mcp_gateway.MCPGatewayService;

import java.util.List;
import java.util.Map;

/** Fetches tool definitions from the review container after deployment. */
public class AppFetchingToolsProcessor implements AppToolStateProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AppFetchingToolsProcessor.class);

    private final MCPGatewayService mcpGatewayService;
    private final ReviewContainerService reviewContainerService;

    public AppFetchingToolsProcessor(MCPGatewayService mcpGatewayService,
            ReviewContainerService reviewContainerService) {
        this.mcpGatewayService = mcpGatewayService;
        this.reviewContainerService = reviewContainerService;
    }

    @Override
    public ToolStatus getStatus() {
        return ToolStatus.FETCHING_TOOLS;
    }

    @Override
    public void process(ToolEntity tool) {
        logger.info("Tool {} entered FETCHING_TOOLS state", tool.getId());

        try {
            Map<String, Object> installCommand = tool.getInstallCommand();
            if (installCommand == null || installCommand.isEmpty()) {
                throw new BusinessException("安装命令为空");
            }

            HostedMcpInstallCommandHelper.NormalizedInstallCommand normalizedInstallCommand = HostedMcpInstallCommandHelper
                    .normalizeInstallCommand(installCommand, tool.getMcpServerName(), tool.getName());
            tool.setInstallCommand(normalizedInstallCommand.installCommand());
            String mcpServerName = normalizedInstallCommand.serverName();
            tool.setMcpServerName(mcpServerName);

            ReviewContainerService.ReviewContainerConnection reviewConnection = reviewContainerService
                    .getReviewContainerConnection();

            logger.info("Fetching tool definitions from review container {}:{} for server {}",
                    reviewConnection.getIpAddress(), reviewConnection.getPort(), mcpServerName);

            List<ToolDefinition> toolDefinitions = fetchToolDefinitionsWithRetry(mcpServerName, reviewConnection);
            if (toolDefinitions == null || toolDefinitions.isEmpty()) {
                throw new BusinessException("从审核容器获取工具列表失败或为空");
            }

            tool.setToolList(toolDefinitions);
            logger.info("Fetched {} tool definitions for {}", toolDefinitions.size(), mcpServerName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Fetching tool definitions interrupted for tool {}", tool.getId(), e);
            throw new BusinessException("获取工具列表过程中被中断: " + e.getMessage(), e);
        } catch (BusinessException e) {
            logger.error("Fetching tool definitions failed for tool {}: {}", tool.getId(), e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error while fetching tool definitions for tool {}", tool.getId(), e);
            throw new BusinessException("从审核容器获取工具列表过程中发生意外错误: " + e.getMessage(), e);
        }
    }

    @Override
    public ToolStatus getNextStatus() {
        return ToolStatus.MANUAL_REVIEW;
    }

    private List<ToolDefinition> fetchToolDefinitionsWithRetry(String mcpServerName,
            ReviewContainerService.ReviewContainerConnection reviewConnection) throws Exception {
        BusinessException lastBusinessException = null;
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                return mcpGatewayService.listToolsFromReviewContainer(mcpServerName, reviewConnection.getIpAddress(),
                        reviewConnection.getPort());
            } catch (BusinessException ex) {
                lastBusinessException = ex;
                logger.warn("Fetch tool list attempt {}/5 failed for {}: {}", attempt, mcpServerName, ex.getMessage());
                if (attempt < 5) {
                    // A successful deployment is usually ready immediately. Delay only after
                    // a failed probe, with a small bounded backoff for slow-starting tools.
                    Thread.sleep(Math.min(1000L * attempt, 3000L));
                }
            }
        }
        throw lastBusinessException == null ? new BusinessException("获取工具列表失败") : lastBusinessException;
    }
}
