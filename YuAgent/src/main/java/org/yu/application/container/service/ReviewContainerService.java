package org.yu.application.container.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.yu.application.container.dto.ContainerDTO;
import org.yu.infrastructure.exception.BusinessException;

/**
 * Service for managing the review container used during MCP tool validation.
 */
@Service
public class ReviewContainerService {

    private static final Logger logger = LoggerFactory.getLogger(ReviewContainerService.class);

    private final ContainerAppService containerAppService;

    public ReviewContainerService(ContainerAppService containerAppService) {
        this.containerAppService = containerAppService;
    }

    public ReviewContainerConnection getReviewContainerConnection() {
        try {
            ContainerDTO reviewContainer = containerAppService.getOrCreateReviewContainer();
            Integer accessPort = resolveContainerAccessPort(reviewContainer);

            if (reviewContainer.getIpAddress() == null || accessPort == null) {
                logger.error("Review container network info incomplete: ip={}, internalPort={}, externalPort={}, status={}",
                        reviewContainer.getIpAddress(), reviewContainer.getInternalPort(),
                        reviewContainer.getExternalPort(), reviewContainer.getStatus());
                throw new BusinessException("审核容器网络配置不完整，容器状态: " + reviewContainer.getStatus());
            }

            logger.info("Review container connection ready: {}:{} ({})", reviewContainer.getIpAddress(), accessPort,
                    reviewContainer.getStatus());
            return new ReviewContainerConnection(reviewContainer.getIpAddress(), accessPort, reviewContainer.getId(),
                    reviewContainer.getName());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to resolve review container connection", e);
            throw new BusinessException("审核容器服务异常: " + e.getMessage());
        }
    }

    public ReviewContainerHealthStatus checkReviewContainerHealth() {
        try {
            ContainerDTO reviewContainer = containerAppService.getOrCreateReviewContainer();
            if (!isContainerHealthy(reviewContainer)) {
                return new ReviewContainerHealthStatus(false, "审核容器状态异常: " + reviewContainer.getStatus(),
                        reviewContainer);
            }
            return new ReviewContainerHealthStatus(true, "审核容器健康", reviewContainer);
        } catch (Exception e) {
            logger.error("Review container health check failed", e);
            return new ReviewContainerHealthStatus(false, "健康检查失败: " + e.getMessage(), null);
        }
    }

    public ContainerDTO recreateReviewContainer() {
        logger.info("Recreating review container");
        try {
            return containerAppService.createReviewContainer();
        } catch (Exception e) {
            logger.error("Failed to recreate review container", e);
            throw new BusinessException("重新创建审核容器失败: " + e.getMessage());
        }
    }

    private boolean isContainerHealthy(ContainerDTO container) {
        if (container == null) {
            return false;
        }

        boolean isRunning = "RUNNING".equals(String.valueOf(container.getStatus()));
        boolean hasNetworkInfo = container.getIpAddress() != null && resolveContainerAccessPort(container) != null;
        boolean hasDockerContainerId = container.getDockerContainerId() != null;

        boolean healthy = isRunning && hasNetworkInfo && hasDockerContainerId;
        if (!healthy) {
            logger.debug("Review container basic health failed: containerId={}, running={}, networkInfo={}, dockerId={}",
                    container.getId(), isRunning, hasNetworkInfo, hasDockerContainerId);
        }
        return healthy;
    }

    private Integer resolveContainerAccessPort(ContainerDTO container) {
        if (container == null || container.getIpAddress() == null) {
            return null;
        }
        if (isLocalAddress(container.getIpAddress())) {
            return container.getExternalPort();
        }
        return container.getInternalPort();
    }

    private boolean isLocalAddress(String ipAddress) {
        return "localhost".equalsIgnoreCase(ipAddress) || "host.docker.internal".equalsIgnoreCase(ipAddress)
                || "127.0.0.1".equals(ipAddress)
                || "::1".equals(ipAddress) || "0:0:0:0:0:0:0:1".equals(ipAddress);
    }

    public static class ReviewContainerConnection {
        private final String ipAddress;
        private final Integer port;
        private final String containerId;
        private final String containerName;

        public ReviewContainerConnection(String ipAddress, Integer port, String containerId, String containerName) {
            this.ipAddress = ipAddress;
            this.port = port;
            this.containerId = containerId;
            this.containerName = containerName;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public Integer getPort() {
            return port;
        }

        public String getContainerId() {
            return containerId;
        }

        public String getContainerName() {
            return containerName;
        }

        public String getBaseUrl() {
            return "http://" + ipAddress + ":" + port;
        }

        @Override
        public String toString() {
            return "ReviewContainerConnection{" + "ipAddress='" + ipAddress + '\'' + ", port=" + port
                    + ", containerId='" + containerId + '\'' + ", containerName='" + containerName + '\'' + '}';
        }
    }

    public static class ReviewContainerHealthStatus {
        private final boolean healthy;
        private final String message;
        private final ContainerDTO container;

        public ReviewContainerHealthStatus(boolean healthy, String message, ContainerDTO container) {
            this.healthy = healthy;
            this.message = message;
            this.container = container;
        }

        public boolean isHealthy() {
            return healthy;
        }

        public String getMessage() {
            return message;
        }

        public ContainerDTO getContainer() {
            return container;
        }
    }
}
