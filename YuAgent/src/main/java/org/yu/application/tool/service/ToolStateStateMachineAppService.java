package org.yu.application.tool.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.yu.application.container.service.ReviewContainerService;
import org.yu.application.tool.service.state.AppToolStateProcessor;
import org.yu.application.tool.service.state.impl.AppDeployingProcessor;
import org.yu.application.tool.service.state.impl.AppFetchingToolsProcessor;
import org.yu.application.tool.service.state.impl.AppGithubUrlValidateProcessor;
import org.yu.application.tool.service.state.impl.AppManualReviewProcessor;
import org.yu.application.tool.service.state.impl.AppPublishingProcessor;
import org.yu.application.tool.service.state.impl.AppWaitingReviewProcessor;
import org.yu.domain.tool.constant.ToolStatus;
import org.yu.domain.tool.model.ToolEntity;
import org.yu.domain.tool.service.ToolDomainService;
import org.yu.infrastructure.exception.BusinessException;
import org.yu.infrastructure.github.GitHubService;
import org.yu.infrastructure.mcp_gateway.MCPGatewayService;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Application-level tool state machine.
 *
 * <p>
 * This layer coordinates processors that depend on infrastructure services and drives the end-to-end
 * review/deploy/fetch workflow for uploaded MCP tools. */
@Service
public class ToolStateStateMachineAppService {

    private static final Logger logger = LoggerFactory.getLogger(ToolStateStateMachineAppService.class);
    private static final int CORE_POOL_SIZE = 5;
    private static final int MAX_POOL_SIZE = 10;
    private static final int QUEUE_CAPACITY = 100;

    private final ToolDomainService toolDomainService;
    private final MCPGatewayService mcpGatewayService;
    private final GitHubService gitHubService;
    private final ReviewContainerService reviewContainerService;
    private final ObjectProvider<ToolAppService> toolAppServiceProvider;

    private final Map<ToolStatus, AppToolStateProcessor> appProcessorMap = new HashMap<>();
    private final ExecutorService executorService;

    @Value("${tool.review.auto-approve:false}")
    private boolean autoApproveManualReview;

    public ToolStateStateMachineAppService(ToolDomainService toolDomainService, MCPGatewayService mcpGatewayService,
            GitHubService gitHubService, ReviewContainerService reviewContainerService,
            ObjectProvider<ToolAppService> toolAppServiceProvider) {
        this.toolDomainService = toolDomainService;
        this.mcpGatewayService = mcpGatewayService;
        this.gitHubService = gitHubService;
        this.reviewContainerService = reviewContainerService;
        this.toolAppServiceProvider = toolAppServiceProvider;
        this.executorService = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY), runnable -> {
                    Thread thread = new Thread(runnable, "app-tool-state-processor-thread");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @PostConstruct
    public void init() {
        registerAppProcessor(new AppWaitingReviewProcessor());
        registerAppProcessor(new AppGithubUrlValidateProcessor(gitHubService));
        registerAppProcessor(new AppDeployingProcessor(mcpGatewayService, reviewContainerService));
        registerAppProcessor(new AppFetchingToolsProcessor(mcpGatewayService, reviewContainerService));
        registerAppProcessor(new AppManualReviewProcessor());
        registerAppProcessor(new AppPublishingProcessor(gitHubService));

        logger.info("Initialized {} app-level tool state processors", appProcessorMap.size());
    }

    @PreDestroy
    public void destroy() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void registerAppProcessor(AppToolStateProcessor processor) {
        AppToolStateProcessor existing = appProcessorMap.put(processor.getStatus(), processor);
        if (existing != null) {
            logger.warn("Tool status processor overridden: status={}, old={}, new={}", processor.getStatus(),
                    existing.getClass().getName(), processor.getClass().getName());
        }
    }

    public void submitToolForProcessing(ToolEntity toolEntity) {
        if (toolEntity == null) {
            throw new BusinessException("Tool does not exist");
        }

        logger.info("Queue tool state processing: toolId={}, status={}", toolEntity.getId(), toolEntity.getStatus());
        executorService.submit(() -> processToolState(toolEntity));
    }

    public void processToolState(ToolEntity toolEntity) {
        ToolStatus currentStatus = toolEntity.getStatus();
        AppToolStateProcessor appProcessor = appProcessorMap.get(currentStatus);
        if (appProcessor != null) {
            processProcessor(toolEntity, appProcessor);
        }
    }

    private void processProcessor(ToolEntity toolEntity, AppToolStateProcessor processor) {
        ToolStatus initialStatus = toolEntity.getStatus();
        logger.info("Start processing tool state: toolId={}, status={}", toolEntity.getId(), initialStatus);

        try {
            processor.process(toolEntity);

            ToolStatus nextStatus = processor.getNextStatus();
            if (nextStatus == null || nextStatus == initialStatus) {
                logger.info("Tool state processing finished without auto transition: toolId={}, status={}",
                        toolEntity.getId(), initialStatus);
                return;
            }

            toolEntity.setStatus(nextStatus);
            toolDomainService.updateToolEntity(toolEntity);
            logger.info("Tool state transitioned: toolId={}, from={}, to={}", toolEntity.getId(), initialStatus,
                    nextStatus);

            if (nextStatus == ToolStatus.MANUAL_REVIEW) {
                if (autoApproveManualReview) {
                    autoApproveTool(toolEntity);
                    return;
                }

                logger.info("Tool entered manual review and is waiting for admin approval: toolId={}",
                        toolEntity.getId());
                return;
            }

            processToolState(toolEntity);
        } catch (Exception e) {
            logger.error("Tool state processing failed: toolId={}, status={}, error={}", toolEntity.getId(),
                    initialStatus, e.getMessage(), e);

            toolEntity.setStatus(ToolStatus.FAILED);
            toolEntity.setFailedStepStatus(initialStatus);
            toolEntity.setRejectReason("状态处理失败: " + e.getMessage());
            toolDomainService.updateToolEntity(toolEntity);
        }
    }

    public void autoApprovePendingManualReview(ToolEntity toolEntity) {
        if (toolEntity == null) {
            throw new BusinessException("Tool does not exist");
        }
        autoApproveTool(toolEntity);
    }

    private void autoApproveTool(ToolEntity toolEntity) {
        logger.info("Auto-approving manual review for toolId={}", toolEntity.getId());
        toolEntity.setStatus(ToolStatus.APPROVED);
        toolDomainService.updateToolEntity(toolEntity);

        ToolAppService toolAppService = toolAppServiceProvider.getIfAvailable();
        if (toolAppService != null) {
            toolAppService.autoInstallApprovedTool(toolEntity.getId());
        }
    }

    public String manualReviewComplete(ToolEntity tool, boolean approved) {
        String toolId = toolDomainService.manualReviewComplete(tool, approved);

        if (approved) {
            logger.info("Manual review approved: toolId={}", toolId);
            submitToolForProcessing(tool);
        } else {
            logger.info("Manual review rejected: toolId={}", toolId);
        }

        return toolId;
    }
}
