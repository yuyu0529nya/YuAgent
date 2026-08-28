package org.yu.application.tool.service.state.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yu.domain.tool.model.ToolEntity;
import org.yu.infrastructure.exception.BusinessException;
import org.yu.infrastructure.github.GitHubService;

import java.nio.file.Path;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AppPublishingProcessorTest {

    private final GitHubService gitHubService = mock(GitHubService.class);
    private final AppPublishingProcessor processor = new AppPublishingProcessor(gitHubService);

    @Test
    void shouldSkipPublishingForManagedMcpSource() {
        ToolEntity tool = new ToolEntity();
        tool.setId("tool-3");
        tool.setName("aliyun-weather");
        tool.setUploadUrl("https://dashscope.aliyuncs.com/api/v1/mcps/weather/sse");

        processor.process(tool);

        verifyNoInteractions(gitHubService);
    }

    @Test
    void shouldSkipPublishingForGitHubLookalikeDomain() {
        ToolEntity tool = new ToolEntity();
        tool.setId("tool-4");
        tool.setName("lookalike");
        tool.setUploadUrl("https://github.com.example.com/owner/repository");

        processor.process(tool);

        verifyNoInteractions(gitHubService);
    }

    @Test
    void shouldKeepSourcePathInsideExtractedRepository(@TempDir Path tempDirectory) {
        Path root = tempDirectory.resolve("repository");
        assertEquals(root.toAbsolutePath().normalize(), AppPublishingProcessor.resolveSourcePath(root, "."));
        assertThrows(BusinessException.class, () -> AppPublishingProcessor.resolveSourcePath(root, "../outside"));
    }
}
