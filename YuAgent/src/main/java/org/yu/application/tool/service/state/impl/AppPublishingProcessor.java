package org.yu.application.tool.service.state.impl;

import net.lingala.zip4j.ZipFile;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yu.application.tool.service.state.AppToolStateProcessor;
import org.yu.domain.tool.constant.ToolStatus;
import org.yu.domain.tool.model.ToolEntity;
import org.yu.domain.tool.model.dto.GitHubRepoInfo;
import org.yu.infrastructure.exception.BusinessException;
import org.yu.infrastructure.github.GitHubService;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Publishes approved tools into the internal GitHub repository when the source is a GitHub repo.
 *
 * <p>
 * Hosted MCP services such as Aliyun Bailian are already deployable by installCommand, so they skip the repository
 * publishing step.
 * </p>
 */
public class AppPublishingProcessor implements AppToolStateProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AppPublishingProcessor.class);

    private final GitHubService gitHubService;

    public AppPublishingProcessor(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    @Override
    public ToolStatus getStatus() {
        return ToolStatus.APPROVED;
    }

    @Override
    public void process(ToolEntity tool) {
        String sourceUrl = tool.getUploadUrl();
        if (sourceUrl == null || sourceUrl.trim().isEmpty()) {
            throw new BusinessException("工具来源地址为空，无法发布。");
        }

        if (!isGitHubUrl(sourceUrl)) {
            logger.info("Skip publishing for managed MCP tool {} (source: {})", tool.getId(), sourceUrl);
            return;
        }

        Path tempDownloadPath = null;
        Path tempUnzipPath = null;
        try {
            GitHubRepoInfo sourceRepoInfo = gitHubService.resolveSourceRepoInfoWithLatestCommitIfNoRef(sourceUrl);
            String version = sourceRepoInfo.getRef();
            if (version == null || version.trim().isEmpty()) {
                throw new BusinessException("无法确定源 GitHub 仓库的版本号(ref/commit SHA)用于发布。");
            }

            String sanitizedVersion = version.replaceAll("[^a-zA-Z0-9_.-]", "_");
            tempDownloadPath = gitHubService.downloadRepositoryArchive(sourceRepoInfo);

            tempUnzipPath = Files.createTempDirectory("unzip-" + UUID.randomUUID().toString().substring(0, 8));
            try (ZipFile zipFile = new ZipFile(tempDownloadPath.toFile())) {
                zipFile.extractAll(tempUnzipPath.toString());
            }

            Path actualContentRoot = findActualContentRoot(tempUnzipPath, sourceRepoInfo.getRepoName());
            if (actualContentRoot == null) {
                throw new BusinessException("无法识别 GitHub 压缩包的实际内容根目录。");
            }

            Path sourcePathToPublish = actualContentRoot;
            if (sourceRepoInfo.getPathInRepo() != null && !sourceRepoInfo.getPathInRepo().isEmpty()) {
                sourcePathToPublish = resolveSourcePath(actualContentRoot, sourceRepoInfo.getPathInRepo());
                if (!Files.exists(sourcePathToPublish) || !Files.isDirectory(sourcePathToPublish)) {
                    throw new BusinessException("源 GitHub 仓库中的路径不存在或不是目录: " + sourceRepoInfo.getPathInRepo());
                }
            }

            String toolIdentifierInTarget = tool.getName() + "-" + sourceRepoInfo.getOwner();
            String targetPathInInternalRepo = toolIdentifierInTarget + "/" + sanitizedVersion;
            String commitMessage = String.format("Publish tool: %s, Version: %s (Source: %s@%s)", tool.getName(),
                    version, sourceRepoInfo.getFullName(), sourceRepoInfo.getRef());
            gitHubService.commitAndPushToTargetRepo(sourcePathToPublish, targetPathInInternalRepo, commitMessage);

            logger.info("Published tool {} version {} to internal repository path {}", tool.getName(), version,
                    targetPathInInternalRepo);
        } catch (BusinessException | IOException | GitAPIException e) {
            logger.error("Publishing tool {} (ID: {}) failed: {}", tool.getName(), tool.getId(), e.getMessage(), e);
            throw new BusinessException("发布工具到内部仓库失败: " + e.getMessage(), e);
        } finally {
            cleanupTemporaryFiles(tempDownloadPath, tempUnzipPath);
        }
    }

    @Override
    public ToolStatus getNextStatus() {
        return null;
    }

    private boolean isGitHubUrl(String sourceUrl) {
        try {
            URI uri = new URI(sourceUrl.trim());
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && "github.com".equalsIgnoreCase(host);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    static Path resolveSourcePath(Path contentRoot, String pathInRepo) {
        Path normalizedRoot = contentRoot.toAbsolutePath().normalize();
        Path resolvedPath = normalizedRoot.resolve(pathInRepo).normalize();
        if (!resolvedPath.startsWith(normalizedRoot)) {
            throw new BusinessException("源 GitHub 仓库路径不能越出仓库根目录: " + pathInRepo);
        }
        return resolvedPath;
    }

    private void cleanupTemporaryFiles(Path tempDownloadPath, Path tempUnzipPath) {
        try {
            if (tempDownloadPath != null && Files.exists(tempDownloadPath)) {
                Files.delete(tempDownloadPath);
            }
            if (tempUnzipPath != null && Files.exists(tempUnzipPath)) {
                FileUtils.deleteDirectory(tempUnzipPath.toFile());
            }
        } catch (IOException e) {
            logger.warn("Failed to cleanup temporary publish files: {}", e.getMessage());
        }
    }

    private Path findActualContentRoot(Path unzipDir, String repoNameHint) throws IOException {
        List<Path> subDirs;
        try (var stream = Files.list(unzipDir)) {
            subDirs = stream.filter(Files::isDirectory).toList();
        }

        if (subDirs.size() == 1) {
            return subDirs.get(0);
        }

        if (repoNameHint != null && !repoNameHint.isEmpty()) {
            for (Path subDir : subDirs) {
                if (subDir.getFileName().toString().toLowerCase(Locale.ROOT)
                        .contains(repoNameHint.toLowerCase(Locale.ROOT))) {
                    return subDir;
                }
            }
        }

        return unzipDir;
    }
}
