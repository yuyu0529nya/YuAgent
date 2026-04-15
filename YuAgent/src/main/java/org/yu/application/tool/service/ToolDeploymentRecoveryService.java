package org.yu.application.tool.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.yu.domain.tool.model.ToolEntity;
import org.yu.domain.tool.service.ToolDomainService;

import java.util.List;

/**
 * Retries transient MCP deployment failures after review container/network recovery.
 */
@Service
public class ToolDeploymentRecoveryService {

    private static final Logger logger = LoggerFactory.getLogger(ToolDeploymentRecoveryService.class);

    private final ToolDomainService toolDomainService;
    private final ToolStateStateMachineAppService toolStateStateMachineAppService;

    @Value("${tool.review.auto-approve:false}")
    private boolean autoApproveManualReview;

    public ToolDeploymentRecoveryService(ToolDomainService toolDomainService,
            ToolStateStateMachineAppService toolStateStateMachineAppService) {
        this.toolDomainService = toolDomainService;
        this.toolStateStateMachineAppService = toolStateStateMachineAppService;
    }

    @Scheduled(initialDelay = 20000, fixedDelay = 30000)
    public void retryRecoverableFailures() {
        List<ToolEntity> tools = toolDomainService.listRecoverableFailedTools(10);
        if (tools.isEmpty()) {
            autoApproveManualReviewTools();
            return;
        }

        for (ToolEntity tool : tools) {
            try {
                ToolEntity retriedTool = toolDomainService.resetFailedToolForRetry(tool.getId());
                logger.warn("Retrying recoverable tool deployment failure: toolId={}, status={}", retriedTool.getId(),
                        retriedTool.getStatus());
                toolStateStateMachineAppService.submitToolForProcessing(retriedTool);
            } catch (Exception e) {
                logger.error("Failed to retry recoverable tool deployment: toolId={}", tool.getId(), e);
            }
        }

        autoApproveManualReviewTools();
    }

    private void autoApproveManualReviewTools() {
        if (!autoApproveManualReview) {
            return;
        }

        List<ToolEntity> tools = toolDomainService.listAutoApprovableManualReviewTools(10);
        for (ToolEntity tool : tools) {
            try {
                logger.info("Auto-approving stuck manual review tool: toolId={}", tool.getId());
                toolStateStateMachineAppService.autoApprovePendingManualReview(tool);
            } catch (Exception e) {
                logger.error("Failed to auto-approve manual review tool: toolId={}", tool.getId(), e);
            }
        }
    }
}
