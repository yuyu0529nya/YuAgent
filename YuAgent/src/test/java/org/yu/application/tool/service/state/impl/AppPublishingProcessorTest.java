package org.yu.application.tool.service.state.impl;

import org.junit.jupiter.api.Test;
import org.yu.domain.tool.model.ToolEntity;
import org.yu.infrastructure.github.GitHubService;

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
}
