package org.yu.application.tool.service.state.impl;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.yu.domain.tool.constant.UploadType;
import org.yu.domain.tool.model.ToolEntity;
import org.yu.infrastructure.github.GitHubService;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AppGithubUrlValidateProcessorTest {

    @Test
    void shouldSkipGithubApiValidationForLocalCommandBasedMcp() throws IOException {
        GitHubService gitHubService = Mockito.mock(GitHubService.class);
        AppGithubUrlValidateProcessor processor = new AppGithubUrlValidateProcessor(gitHubService);

        ToolEntity tool = new ToolEntity();
        tool.setId("tool-local");
        tool.setUploadUrl("https://github.com/xhyqaq/surge-mcp-server");
        tool.setUploadType(UploadType.ZIP);
        tool.setInstallCommand(Map.of("mcpServers", Map.of("surge-local-test",
                Map.of("command", "npx", "args", List.of("-y", "https://github.com/xhyqaq/surge-mcp-server")))));

        assertDoesNotThrow(() -> processor.process(tool));
        verify(gitHubService, never()).validateGitHubRepoRefAndPath(any());
    }
}
