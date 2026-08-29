package org.yu.application.conversation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.yu.application.container.dto.ContainerDTO;
import org.yu.application.container.service.ContainerAppService;
import org.yu.domain.container.constant.ContainerStatus;
import org.yu.domain.tool.model.ToolEntity;
import org.yu.domain.tool.service.ToolDomainService;
import org.yu.infrastructure.mcp_gateway.MCPGatewayService;

class McpUrlProviderServiceTest {

    @Test
    void shouldResolveToolOnlyOnceWhenBuildingGlobalToolUrl() {
        MCPGatewayService gatewayService = mock(MCPGatewayService.class);
        ContainerAppService containerAppService = mock(ContainerAppService.class);
        ToolDomainService toolDomainService = mock(ToolDomainService.class);
        McpUrlProviderService service = new McpUrlProviderService(gatewayService, containerAppService,
                toolDomainService);
        ToolEntity tool = new ToolEntity();
        tool.setIsGlobal(true);
        ContainerDTO container = runningContainer();

        when(toolDomainService.getToolByServerNameForUsage("weather", "user-id")).thenReturn(tool);
        when(containerAppService.getOrCreateReviewContainer()).thenReturn(container);
        when(gatewayService.buildUserContainerUrl("weather", "10.0.0.5", 8080))
                .thenReturn("http://10.0.0.5:8080/weather?api_key=secret");

        assertEquals("http://10.0.0.5:8080/weather?api_key=secret", service.getSSEUrl("weather", "user-id"));
        verify(toolDomainService, times(1)).getToolByServerNameForUsage("weather", "user-id");
    }

    private ContainerDTO runningContainer() {
        ContainerDTO container = new ContainerDTO();
        container.setId("review-container");
        container.setStatus(ContainerStatus.RUNNING);
        container.setDockerContainerId("docker-id");
        container.setIpAddress("10.0.0.5");
        container.setInternalPort(8080);
        return container;
    }
}
