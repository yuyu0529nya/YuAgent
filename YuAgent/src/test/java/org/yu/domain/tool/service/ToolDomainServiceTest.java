package org.yu.domain.tool.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.yu.domain.tool.constant.UploadType;
import org.yu.domain.tool.model.ToolEntity;
import org.yu.domain.tool.repository.ToolRepository;
import org.yu.domain.tool.repository.ToolVersionRepository;
import org.yu.domain.tool.repository.UserToolRepository;
import org.yu.domain.user.repository.UserRepository;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

class ToolDomainServiceTest {

    @Test
    void shouldReturnNullWhenNoInstalledToolMatchesServerName() {
        ToolRepository toolRepository = Mockito.mock(ToolRepository.class);
        ToolVersionRepository toolVersionRepository = Mockito.mock(ToolVersionRepository.class);
        UserToolRepository userToolRepository = Mockito.mock(UserToolRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        when(userToolRepository.selectOne(any())).thenReturn(null);

        ToolDomainService toolDomainService = new ToolDomainService(toolRepository, toolVersionRepository,
                userToolRepository, userRepository);

        assertNull(toolDomainService.getUserInstalledToolByServerName("missing-server", "u1"));
    }

    @Test
    void shouldClassifyCommandBasedGithubSourceAsLocalUpload() {
        ToolRepository toolRepository = Mockito.mock(ToolRepository.class);
        ToolVersionRepository toolVersionRepository = Mockito.mock(ToolVersionRepository.class);
        UserToolRepository userToolRepository = Mockito.mock(UserToolRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);

        when(userToolRepository.selectCount(any())).thenReturn(0L);
        doNothing().when(toolRepository).checkInsert(any(ToolEntity.class));

        ToolDomainService toolDomainService = new ToolDomainService(toolRepository, toolVersionRepository,
                userToolRepository, userRepository);

        ToolEntity tool = new ToolEntity();
        tool.setName("surge");
        tool.setUserId("u1");
        tool.setUploadUrl("https://github.com/xhyqaq/surge-mcp-server");
        tool.setInstallCommand(Map.of("mcpServers", Map.of("surge-local-test",
                Map.of("command", "npx", "args", List.of("-y", "https://github.com/xhyqaq/surge-mcp-server")))));

        toolDomainService.createTool(tool);

        assertEquals(UploadType.ZIP, tool.getUploadType());
        assertEquals("surge-local-test", tool.getMcpServerName());
    }
}
