package org.yu.application.tool.service.state.impl;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yu.application.container.service.ReviewContainerService;
import org.yu.application.tool.service.state.AppToolStateProcessor;
import org.yu.domain.tool.constant.ToolStatus;
import org.yu.domain.tool.model.ToolEntity;
import org.yu.infrastructure.exception.BusinessException;
import org.yu.infrastructure.mcp_gateway.MCPGatewayService;
import org.yu.infrastructure.utils.JsonUtils;

public class AppDeployingProcessor implements AppToolStateProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AppDeployingProcessor.class);

    private final MCPGatewayService mcpGatewayService;
    private final ReviewContainerService reviewContainerService;

    public AppDeployingProcessor(MCPGatewayService mcpGatewayService, ReviewContainerService reviewContainerService) {
        this.mcpGatewayService = mcpGatewayService;
        this.reviewContainerService = reviewContainerService;
    }

    @Override
    public ToolStatus getStatus() {
        return ToolStatus.DEPLOYING;
    }

    @Override
    public void process(ToolEntity tool) {
        logger.info("Tool {} entered DEPLOYING state", tool.getId());

        try {
            Map<String, Object> installCommand = tool.getInstallCommand();
            if (installCommand == null || installCommand.isEmpty()) {
                throw new BusinessException("工具安装命令为空，无法部署");
            }

            String installCommandJson = JsonUtils.toJsonString(installCommand);
            ReviewContainerService.ReviewContainerConnection reviewConnection = reviewContainerService
                    .getReviewContainerConnection();
            boolean deploySuccess = mcpGatewayService.deployTool(installCommandJson, reviewConnection.getIpAddress(),
                    reviewConnection.getPort());

            if (!deploySuccess) {
                throw new BusinessException("MCP Gateway 部署返回非成功状态");
            }

            logger.info("Tool {} deployed successfully", tool.getId());
        } catch (BusinessException e) {
            logger.error("Deploy tool {} failed: {}", tool.getId(), e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error while deploying tool {}", tool.getId(), e);
            throw new BusinessException("部署工具过程中发生意外错误: " + e.getMessage(), e);
        }
    }

    @Override
    public ToolStatus getNextStatus() {
        return ToolStatus.FETCHING_TOOLS;
    }
}
