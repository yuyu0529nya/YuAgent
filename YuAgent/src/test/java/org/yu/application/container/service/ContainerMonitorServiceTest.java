package org.yu.application.container.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yu.domain.container.constant.ContainerStatus;
import org.yu.domain.container.model.ContainerEntity;
import org.yu.domain.container.service.ContainerDomainService;
import org.yu.infrastructure.docker.DockerService;
import org.yu.infrastructure.entity.Operator;

class ContainerMonitorServiceTest {

    private ContainerDomainService containerDomainService;
    private DockerService dockerService;
    private ContainerMonitorService service;

    @BeforeEach
    void setUp() {
        containerDomainService = mock(ContainerDomainService.class);
        dockerService = mock(DockerService.class);
        service = new ContainerMonitorService(containerDomainService, dockerService);
    }

    @Test
    void shouldMarkContainerAsErrorWhenDockerIdIsMissing() {
        ContainerEntity container = container("container-1", null, ContainerStatus.RUNNING);
        when(containerDomainService.getMonitoringContainers()).thenReturn(List.of(container));

        service.checkContainerStatus();

        verify(containerDomainService).markContainerError("container-1", "缺少Docker容器ID，需要手动恢复", Operator.ADMIN);
        verify(dockerService, never()).getContainerActualStatus(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldMarkMissingDockerContainerAsStopped() {
        ContainerEntity container = container("container-1", "docker-1", ContainerStatus.RUNNING);
        when(containerDomainService.getMonitoringContainers()).thenReturn(List.of(container));
        when(dockerService.getContainerActualStatus("docker-1"))
                .thenReturn(new DockerService.ContainerActualStatus(false, null, "not found"));

        service.checkContainerStatus();

        verify(containerDomainService).updateContainerStatus("container-1", ContainerStatus.STOPPED, Operator.ADMIN,
                null);
    }

    @Test
    void shouldMarkContainerRunningAfterSuccessfulRecovery() {
        ContainerEntity container = container("container-1", "docker-1", ContainerStatus.STOPPED);
        when(containerDomainService.getMonitoringContainers()).thenReturn(List.of(container));
        when(dockerService.getContainerActualStatus("docker-1"))
                .thenReturn(new DockerService.ContainerActualStatus(true, "exited", "stopped"));
        when(dockerService.forceStartContainerIfExists("docker-1"))
                .thenReturn(new DockerService.ContainerRecoveryResult(true, "started", "容器已启动"));

        service.checkContainerStatus();

        verify(containerDomainService).updateContainerStatus("container-1", ContainerStatus.RUNNING, Operator.ADMIN,
                null);
    }

    @Test
    void shouldSynchronizeDatabaseStatusWhenDockerIsAlreadyRunning() {
        ContainerEntity container = container("container-1", "docker-1", ContainerStatus.STOPPED);
        when(containerDomainService.getMonitoringContainers()).thenReturn(List.of(container));
        when(dockerService.getContainerActualStatus("docker-1"))
                .thenReturn(new DockerService.ContainerActualStatus(true, "running", "healthy"));

        service.checkContainerStatus();

        verify(containerDomainService).updateContainerStatus(eq("container-1"), eq(ContainerStatus.RUNNING),
                eq(Operator.ADMIN), eq(null));
    }

    private ContainerEntity container(String id, String dockerId, ContainerStatus status) {
        ContainerEntity container = new ContainerEntity();
        container.setId(id);
        container.setName(id);
        container.setDockerContainerId(dockerId);
        container.setStatus(status);
        return container;
    }
}
