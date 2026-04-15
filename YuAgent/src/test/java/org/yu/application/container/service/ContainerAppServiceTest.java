package org.yu.application.container.service;

import org.junit.jupiter.api.Test;
import org.yu.application.container.dto.ContainerDTO;
import org.yu.domain.container.constant.ContainerStatus;
import org.yu.domain.container.constant.ContainerType;
import org.yu.domain.container.model.ContainerEntity;
import org.yu.domain.container.model.ContainerTemplateEntity;
import org.yu.domain.container.service.ContainerDomainService;
import org.yu.domain.container.service.ContainerTemplateDomainService;
import org.yu.domain.user.service.UserDomainService;
import org.yu.infrastructure.docker.DockerService;
import org.yu.infrastructure.entity.Operator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContainerAppServiceTest {

    private final ContainerDomainService containerDomainService = mock(ContainerDomainService.class);
    private final ContainerTemplateDomainService templateDomainService = mock(ContainerTemplateDomainService.class);
    private final UserDomainService userDomainService = mock(UserDomainService.class);
    private final DockerService dockerService = mock(DockerService.class);

    private final ContainerAppService service = new ContainerAppService(containerDomainService, templateDomainService,
            userDomainService, dockerService);

    @Test
    void shouldRefreshLegacyReviewContainerToCurrentGatewayTemplate() {
        ContainerEntity legacyContainer = container("review-1", "mcp-gateway-review-system",
                "ghcr.io/lucky-aeon/api-premium-gateway:latest", ContainerType.REVIEW, ContainerStatus.RUNNING);
        legacyContainer.setDockerContainerId("old-review-docker");
        legacyContainer.setExternalPort(32801);
        legacyContainer.setInternalPort(8080);
        legacyContainer.setIpAddress("localhost");
        legacyContainer.setVolumePath("D:/yuagent/data/users/review-system");

        ContainerEntity refreshedContainer = container("review-1", "mcp-gateway-review-system",
                "yuagent-mcp-gateway:latest", ContainerType.REVIEW, ContainerStatus.RUNNING);
        refreshedContainer.setDockerContainerId("new-review-docker");
        refreshedContainer.setExternalPort(32801);
        refreshedContainer.setInternalPort(8080);
        refreshedContainer.setIpAddress("localhost");
        refreshedContainer.setVolumePath("D:/yuagent/data/users/review-system");

        when(containerDomainService.findReviewContainer()).thenReturn(legacyContainer);
        when(containerDomainService.getContainerById("review-1")).thenReturn(refreshedContainer);
        when(templateDomainService.getReviewContainerTemplate()).thenReturn(template(ContainerType.REVIEW));
        when(dockerService.getContainerInfo("old-review-docker")).thenReturn(containerInfo("172.18.0.8", "bridge"));
        when(dockerService.findContainerByName("mcp-gateway-review-system")).thenReturn(null);
        when(dockerService.createAndStartContainer(eq("mcp-gateway-review-system"), any(), eq(32801),
                eq("D:/yuagent/data/users/review-system"), eq(null))).thenReturn("new-review-docker");
        when(dockerService.getContainerInfo("new-review-docker"))
                .thenReturn(containerInfo("172.18.0.9", "yuagent_yuagent-network"));
        when(dockerService.getContainerActualStatus("new-review-docker"))
                .thenReturn(new DockerService.ContainerActualStatus(true, "running", "ok"));
        when(dockerService.isContainerNetworkAccessible("localhost", 32801)).thenReturn(true);

        ContainerDTO result = service.getOrCreateReviewContainer();

        assertNotNull(result);
        assertEquals("review-1", result.getId());
        assertEquals("yuagent-mcp-gateway:latest", result.getImage());
        verify(dockerService, times(1)).removeContainer("old-review-docker", true);
        verify(containerDomainService, times(1)).resetContainerRuntime("review-1", "yuagent-mcp-gateway:latest", 8080,
                ContainerStatus.STOPPED, null);
        verify(containerDomainService, times(1)).updateContainerStatus("review-1", ContainerStatus.RUNNING,
                Operator.ADMIN, "new-review-docker");
    }

    @Test
    void shouldRefreshLegacyUserContainerToCurrentGatewayTemplate() {
        ContainerEntity legacyContainer = container("user-1", "mcp-gateway-user-12345678",
                "ghcr.io/lucky-aeon/api-premium-gateway:latest", ContainerType.USER, ContainerStatus.RUNNING);
        legacyContainer.setUserId("12345678abcdef");
        legacyContainer.setDockerContainerId("old-user-docker");
        legacyContainer.setExternalPort(32802);
        legacyContainer.setInternalPort(8080);
        legacyContainer.setIpAddress("localhost");
        legacyContainer.setVolumePath("D:/yuagent/data/users/12345678abcdef");

        ContainerEntity refreshedContainer = container("user-1", "mcp-gateway-user-12345678",
                "yuagent-mcp-gateway:latest", ContainerType.USER, ContainerStatus.RUNNING);
        refreshedContainer.setUserId("12345678abcdef");
        refreshedContainer.setDockerContainerId("new-user-docker");
        refreshedContainer.setExternalPort(32802);
        refreshedContainer.setInternalPort(8080);
        refreshedContainer.setIpAddress("localhost");
        refreshedContainer.setVolumePath("D:/yuagent/data/users/12345678abcdef");

        when(containerDomainService.findUserContainer("12345678abcdef")).thenReturn(legacyContainer, refreshedContainer);
        when(containerDomainService.getContainerById("user-1")).thenReturn(refreshedContainer);
        when(templateDomainService.getMcpGatewayTemplate()).thenReturn(template(ContainerType.USER));
        when(dockerService.getContainerInfo("old-user-docker")).thenReturn(containerInfo("172.18.0.7", "bridge"));
        when(dockerService.findContainerByName("mcp-gateway-user-12345678")).thenReturn(null);
        when(dockerService.createAndStartContainer(eq("mcp-gateway-user-12345678"), any(), eq(32802),
                eq("D:/yuagent/data/users/12345678abcdef"), eq("12345678abcdef"))).thenReturn("new-user-docker");
        when(dockerService.getContainerInfo("new-user-docker"))
                .thenReturn(containerInfo("172.18.0.10", "yuagent_yuagent-network"));
        when(dockerService.getContainerActualStatus("new-user-docker"))
                .thenReturn(new DockerService.ContainerActualStatus(true, "running", "ok"));
        when(dockerService.isContainerNetworkAccessible("localhost", 32802)).thenReturn(true);

        ContainerDTO result = service.getUserContainer("12345678abcdef");

        assertNotNull(result);
        assertEquals("user-1", result.getId());
        assertEquals("yuagent-mcp-gateway:latest", result.getImage());
        verify(dockerService, times(1)).removeContainer("old-user-docker", true);
        verify(containerDomainService, times(1)).resetContainerRuntime("user-1", "yuagent-mcp-gateway:latest", 8080,
                ContainerStatus.STOPPED, null);
        verify(containerDomainService, times(1)).updateContainerStatus("user-1", ContainerStatus.RUNNING,
                Operator.ADMIN, "new-user-docker");
    }

    private ContainerEntity container(String id, String name, String image, ContainerType type, ContainerStatus status) {
        ContainerEntity container = new ContainerEntity();
        container.setId(id);
        container.setName(name);
        container.setImage(image);
        container.setType(type);
        container.setStatus(status);
        return container;
    }

    private ContainerTemplateEntity template(ContainerType type) {
        ContainerTemplateEntity template = new ContainerTemplateEntity();
        template.setType(type);
        template.setImage("yuagent-mcp-gateway");
        template.setImageTag("latest");
        template.setInternalPort(8080);
        template.setNetworkMode("yuagent_yuagent-network");
        template.setVolumeMountPath("/app/data");
        template.setCpuLimit(1.0);
        template.setMemoryLimit(512);
        template.setEnabled(true);
        template.setIsDefault(true);
        return template;
    }

    private DockerService.ContainerInfo containerInfo(String ipAddress, String networkMode) {
        DockerService.ContainerInfo info = new DockerService.ContainerInfo();
        info.setName(ipAddress);
        info.setNetworkMode(networkMode);
        return info;
    }
}
