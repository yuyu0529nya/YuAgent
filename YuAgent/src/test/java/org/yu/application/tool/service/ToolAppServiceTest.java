package org.yu.application.tool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.yu.application.tool.dto.ToolVersionDTO;
import org.yu.domain.tool.model.ToolVersionEntity;
import org.yu.domain.tool.model.ToolEntity;
import org.yu.domain.tool.service.ToolDomainService;
import org.yu.domain.tool.service.ToolVersionDomainService;
import org.yu.domain.tool.service.UserToolDomainService;
import org.yu.domain.user.model.UserEntity;
import org.yu.domain.user.service.UserDomainService;
import org.yu.interfaces.dto.tool.request.QueryToolRequest;
import org.yu.infrastructure.exception.BusinessException;

class ToolAppServiceTest {

    @Test
    void shouldRequestOnlyTheRecommendationPage() {
        ToolDomainService toolDomainService = Mockito.mock(ToolDomainService.class);
        UserToolDomainService userToolDomainService = Mockito.mock(UserToolDomainService.class);
        ToolVersionDomainService toolVersionDomainService = Mockito.mock(ToolVersionDomainService.class);
        UserDomainService userDomainService = Mockito.mock(UserDomainService.class);
        ToolStateStateMachineAppService stateMachine = Mockito.mock(ToolStateStateMachineAppService.class);

        ToolVersionEntity tool = toolVersion("tool-1", "user-1");
        Page<ToolVersionEntity> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(tool));
        when(toolVersionDomainService.listToolVersion(Mockito.any())).thenReturn(page);
        when(userToolDomainService.getToolsInstall(List.of("tool-1"))).thenReturn(Map.of("tool-1", 3L));
        when(userDomainService.getByIds(List.of("user-1"))).thenReturn(List.of(user("user-1", "Yu")));

        ToolAppService service = new ToolAppService(toolDomainService, userToolDomainService, toolVersionDomainService,
                userDomainService, stateMachine);

        var recommendations = service.getRecommendTools();

        ArgumentCaptor<QueryToolRequest> requestCaptor = ArgumentCaptor.forClass(QueryToolRequest.class);
        verify(toolVersionDomainService).listToolVersion(requestCaptor.capture());
        assertEquals(1, requestCaptor.getValue().getPage());
        assertEquals(10, requestCaptor.getValue().getPageSize());
        assertEquals(1, recommendations.size());
        assertEquals("Yu", recommendations.get(0).getUserName());
        assertEquals(3L, recommendations.get(0).getInstallCount());
    }

    @Test
    void shouldPreventToolOwnerFromUninstallingOwnTool() {
        ToolDomainService toolDomainService = Mockito.mock(ToolDomainService.class);
        UserToolDomainService userToolDomainService = Mockito.mock(UserToolDomainService.class);
        ToolAppService service = service(toolDomainService, userToolDomainService);
        ToolEntity tool = new ToolEntity();
        tool.setUserId("owner-1");
        when(toolDomainService.getTool("tool-1")).thenReturn(tool);

        assertThrows(BusinessException.class, () -> service.uninstallTool("tool-1", "owner-1"));

        verify(userToolDomainService, never()).delete("tool-1", "owner-1");
    }

    @Test
    void shouldAllowUninstallWhenOriginalToolWasDeleted() {
        ToolDomainService toolDomainService = Mockito.mock(ToolDomainService.class);
        UserToolDomainService userToolDomainService = Mockito.mock(UserToolDomainService.class);
        ToolAppService service = service(toolDomainService, userToolDomainService);
        when(toolDomainService.getTool("tool-1")).thenThrow(new BusinessException("工具不存在: tool-1"));

        service.uninstallTool("tool-1", "user-1");

        verify(userToolDomainService).delete("tool-1", "user-1");
    }

    @Test
    void shouldCheckExactInstalledToolVersionWithoutListingAllTools() {
        ToolDomainService toolDomainService = Mockito.mock(ToolDomainService.class);
        UserToolDomainService userToolDomainService = Mockito.mock(UserToolDomainService.class);
        ToolAppService service = service(toolDomainService, userToolDomainService);
        org.yu.domain.tool.model.UserToolEntity installedTool = new org.yu.domain.tool.model.UserToolEntity();
        installedTool.setVersion("2.0.0");
        when(userToolDomainService.findByToolIdAndUserId("tool-1", "user-1")).thenReturn(installedTool);

        assertTrue(service.isToolVersionInstalled("user-1", "tool-1", "2.0.0"));
        assertFalse(service.isToolVersionInstalled("user-1", "tool-1", "1.0.0"));

        verify(userToolDomainService, never()).listByUserId(Mockito.anyString(), Mockito.any());
    }

    @Test
    void shouldReturnToolVersionWhenItsAuthorNoLongerExists() {
        ToolDomainService toolDomainService = Mockito.mock(ToolDomainService.class);
        UserToolDomainService userToolDomainService = Mockito.mock(UserToolDomainService.class);
        ToolVersionDomainService toolVersionDomainService = Mockito.mock(ToolVersionDomainService.class);
        UserDomainService userDomainService = Mockito.mock(UserDomainService.class);
        ToolAppService service = new ToolAppService(toolDomainService, userToolDomainService, toolVersionDomainService,
                userDomainService, Mockito.mock(ToolStateStateMachineAppService.class));
        ToolVersionEntity version = toolVersion("tool-1", "deleted-user");

        when(toolVersionDomainService.getToolVersion("tool-1", "1.0.0", "viewer-1")).thenReturn(version);
        when(toolVersionDomainService.getToolVersions("tool-1", "viewer-1")).thenReturn(List.of(version));
        when(userDomainService.getUserInfo("deleted-user")).thenReturn(null);
        when(userToolDomainService.getToolsInstall(List.of("tool-1"))).thenReturn(Map.of("tool-1", 2L));

        ToolVersionDTO detail = service.getToolVersionDetail("tool-1", "1.0.0", "viewer-1");

        assertEquals("tool-1", detail.getToolId());
        assertEquals(null, detail.getUserName());
        assertEquals(2L, detail.getInstallCount());
    }

    private ToolAppService service(ToolDomainService toolDomainService, UserToolDomainService userToolDomainService) {
        return new ToolAppService(toolDomainService, userToolDomainService,
                Mockito.mock(ToolVersionDomainService.class), Mockito.mock(UserDomainService.class),
                Mockito.mock(ToolStateStateMachineAppService.class));
    }

    private ToolVersionEntity toolVersion(String toolId, String userId) {
        ToolVersionEntity entity = new ToolVersionEntity();
        entity.setToolId(toolId);
        entity.setUserId(userId);
        entity.setName("地图");
        entity.setCreatedAt(LocalDateTime.of(2026, 8, 27, 12, 0));
        return entity;
    }

    private UserEntity user(String id, String nickname) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setNickname(nickname);
        return entity;
    }
}
