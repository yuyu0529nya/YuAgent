package org.yu.application.tool.service.state.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yu.application.tool.service.state.AppToolStateProcessor;
import org.yu.domain.tool.constant.ToolStatus;
import org.yu.domain.tool.constant.UploadType;
import org.yu.domain.tool.model.ToolEntity;
import org.yu.domain.tool.model.dto.GitHubRepoInfo;
import org.yu.infrastructure.exception.BusinessException;
import org.yu.infrastructure.github.GitHubService;
import org.yu.infrastructure.github.GitHubUrlParser;
import org.yu.infrastructure.mcp_gateway.HostedMcpInstallCommandHelper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Validates the tool source URL before deployment.
 *
 * <p>GitHub repositories still go through the original GitHub API validation,
 * while managed MCP services such as hosted SSE endpoints only need basic URL validation.</p>
 */
public class AppGithubUrlValidateProcessor implements AppToolStateProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AppGithubUrlValidateProcessor.class);

    private final GitHubService gitHubService;

    public AppGithubUrlValidateProcessor(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    @Override
    public ToolStatus getStatus() {
        return ToolStatus.GITHUB_URL_VALIDATE;
    }

    @Override
    public void process(ToolEntity tool) {
        String uploadUrl = tool.getUploadUrl();
        logger.info("Validating tool source URL: {} (toolId: {})", uploadUrl, tool.getId());

        try {
            validateUrlFormat(uploadUrl);
            if (!requiresGitHubValidation(tool)) {
                logger.info("Skipping GitHub-specific validation for managed MCP source: {} (toolId: {})", uploadUrl,
                        tool.getId());
                return;
            }

            GitHubRepoInfo repoInfo = GitHubUrlParser.parseGithubUrl(uploadUrl);
            gitHubService.validateGitHubRepoRefAndPath(repoInfo);
            logger.info("GitHub source URL validated successfully: {} (toolId: {})", uploadUrl, tool.getId());
        } catch (IOException e) {
            logger.error("GitHub API validation failed for tool {}: {}", tool.getId(), e.getMessage(), e);
            throw new BusinessException("验证 GitHub 源地址时发生 API 错误: " + e.getMessage(), e);
        } catch (BusinessException e) {
            logger.error("Source URL validation failed for tool {}: {}", tool.getId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error while validating source URL for tool {}", tool.getId(), e);
            throw new BusinessException("验证工具来源地址时发生意外错误: " + e.getMessage(), e);
        }
    }

    @Override
    public ToolStatus getNextStatus() {
        return ToolStatus.DEPLOYING;
    }

    private void validateUrlFormat(String uploadUrl) {
        if (uploadUrl == null || uploadUrl.trim().isEmpty()) {
            throw new BusinessException("工具来源地址不能为空。");
        }

        try {
            URI uri = new URI(uploadUrl.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new BusinessException("工具来源地址仅支持 http 或 https。");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new BusinessException("工具来源地址缺少有效域名。");
            }
        } catch (URISyntaxException e) {
            throw new BusinessException("工具来源地址格式不合法: " + uploadUrl, e);
        }
    }

    private boolean isGitHubUrl(String uploadUrl) {
        try {
            URI uri = new URI(uploadUrl.trim());
            String host = uri.getHost();
            return host != null && host.toLowerCase(Locale.ROOT).contains("github.com");
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private boolean requiresGitHubValidation(ToolEntity tool) {
        if (tool == null) {
            return false;
        }

        if (tool.getUploadType() != UploadType.GITHUB) {
            return false;
        }

        String transportType = HostedMcpInstallCommandHelper.getHostedTransportType(tool.getInstallCommand());
        if ("stdio".equalsIgnoreCase(transportType)) {
            return false;
        }

        return isGitHubUrl(tool.getUploadUrl());
    }
}
