package org.yu.infrastructure.mcp_gateway;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.yu.domain.tool.model.config.ToolDefinition;
import org.yu.domain.tool.model.config.ToolSpecificationConverter;
import org.yu.infrastructure.config.MCPGatewayProperties;
import org.yu.infrastructure.exception.BusinessException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.yu.infrastructure.utils.JsonUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** MCP Gateway服务 处理与MCP网关的所有API交互 */
@Service
public class MCPGatewayService {

    private static final Logger logger = LoggerFactory.getLogger(MCPGatewayService.class);
    /** The gateway exposes the session-based Streamable HTTP 2025 protocol. */
    private static final String MCP_GATEWAY_PROTOCOL_VERSION = "2025-11-25";

    private final MCPGatewayProperties properties;
    private final CloseableHttpClient httpClient;

    /** 通过构造函数注入配置
     * 
     * @param properties MCP Gateway配置 */
    public MCPGatewayService(MCPGatewayProperties properties) {
        this.properties = properties;
        this.httpClient = createHttpClient();
    }

    /** 初始化时验证配置有效性 */
    @PostConstruct
    public void init() {
        if (properties.getBaseUrl() == null || properties.getBaseUrl().trim().isEmpty()) {
            logger.warn("MCP Gateway基础URL未配置 (mcp.gateway.base-url)");
        }

        if (properties.getApiKey() == null || properties.getApiKey().trim().isEmpty()) {
            logger.warn("MCP Gateway API密钥未配置 (mcp.gateway.api-key)");
        }

        logger.info("MCP Gateway服务已初始化，基础URL: {}", properties.getBaseUrl());
    }

    @PreDestroy
    public void close() {
        try {
            httpClient.close();
        } catch (IOException e) {
            logger.warn("Failed to close MCP Gateway HTTP client", e);
        }
    }

    /** 构建用户容器 Streamable HTTP URL（纯技术方法）
     * 
     * @param mcpServerName 工具服务名称
     * @param containerIp 容器IP地址
     * @param containerPort 容器端口
     * @return 用户容器 Streamable HTTP URL */
    public String buildUserContainerUrl(String mcpServerName, String containerIp, Integer containerPort) {
        String containerBaseUrl = "http://" + containerIp + ":" + containerPort;
        return containerBaseUrl + "/" + mcpServerName + "?api_key=" + properties.getApiKey();
    }

    /** 构建全局工具 Streamable HTTP URL（纯技术方法）
     * 
     * @param mcpServerName 工具服务名称
     * @return 全局工具 Streamable HTTP URL */
    public String buildGlobalSSEUrl(String mcpServerName) {
        // Hosted MCP services are aggregated by the gateway's global SSE
        // endpoint. This also lets the gateway bridge Streamable HTTP services
        // for the application's SSE-only MCP client.
        return properties.getBaseUrl() + "/stream?api_key=" + properties.getApiKey();
    }

    /** 部署工具到MCP Gateway
     * 
     * @param installCommand 安装命令
     * @return 部署成功返回true，否则抛出异常
     * @throws BusinessException 如果API调用失败 */
    public boolean deployTool(String installCommand) {
        String url = properties.getBaseUrl() + "/deploy";
        return deployToolToUrl(installCommand, url);
    }

    /** 部署工具到用户容器（方法重载）
     * 
     * @param installCommand 安装命令
     * @param containerIp 容器IP地址
     * @param containerPort 容器端口
     * @return 部署成功返回true，否则抛出异常
     * @throws BusinessException 如果API调用失败 */
    public boolean deployTool(String installCommand, String containerIp, Integer containerPort) {
        String url = "http://" + containerIp + ":" + containerPort + "/deploy";
        return deployToolToUrl(installCommand, url);
    }

    /** 部署工具到指定URL的通用方法 */
    private boolean deployToolToUrl(String installCommand, String url) {
        try {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + properties.getApiKey());
            httpPost.setEntity(new StringEntity(installCommand, "UTF-8"));

            logger.info("发送 MCP Gateway 工具部署请求");
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity entity = response.getEntity();
                String responseBody = entity != null ? EntityUtils.toString(entity) : null;

                if (statusCode >= 200 && statusCode < 300 && responseBody != null) {
                    Map<String, Object> result = JsonUtils.parseMap(responseBody);
                    if (result == null) {
                        throw new BusinessException("工具部署响应格式无效");
                    }
                    Object successValue = result.get("success");
                    boolean success = successValue instanceof Boolean booleanValue
                            ? booleanValue
                            : Boolean.parseBoolean(String.valueOf(successValue));
                    logger.info("MCP Gateway 工具部署响应已接收，success={}", success);
                    return success;
                } else {
                    String errorMsg = String.format("工具部署失败，状态码: %d，响应: %s", statusCode, responseBody);
                    logger.error(errorMsg);
                    throw new BusinessException(errorMsg);
                }
            }
        } catch (IOException e) {
            throw new BusinessException("调用部署API失败: " + e.getMessage(), e);
        }
    }

    /** 从MCP Gateway获取工具列表
     *
     * @param toolName 可选，特定工具名称
     * @return 工具定义列表
     * @throws BusinessException 如果API调用失败 */
    public List<ToolDefinition> listTools(String toolName) throws Exception {
        String url = properties.getBaseUrl() + "/" + toolName + "?api_key=" + properties.getApiKey();
        StreamableHttpMcpTransport transport = StreamableHttpMcpTransport.builder().url(url)
                .timeout(getMcpClientTimeout()).logRequests(false).logResponses(false).build();
        McpClient client = new DefaultMcpClient.Builder().transport(transport)
                .protocolVersion(MCP_GATEWAY_PROTOCOL_VERSION).initializationTimeout(getMcpClientTimeout())
                .autoHealthCheck(false).build();
        try {
            List<ToolSpecification> toolSpecifications = client.listTools();
            return ToolSpecificationConverter.convert(toolSpecifications);
        } catch (Exception e) {
            logger.error("调用MCP Gateway API失败", e);
            throw new BusinessException("调用MCP Gateway API失败: " + e.getMessage(), e);
        } finally {
            client.close();
        }
    }

    /** 从审核容器获取工具列表
     *
     * @param toolName 工具名称
     * @param containerIp 审核容器IP地址
     * @param containerPort 审核容器端口
     * @return 工具定义列表
     * @throws BusinessException 如果API调用失败 */
    public List<ToolDefinition> listToolsFromReviewContainer(String toolName, String containerIp, Integer containerPort)
            throws Exception {
        String url = "http://" + containerIp + ":" + containerPort + "/" + toolName + "?api_key="
                + properties.getApiKey();

        logger.info("Fetching MCP tools from review container: tool={}, host={}:{}", toolName, containerIp,
                containerPort);

        StreamableHttpMcpTransport transport = StreamableHttpMcpTransport.builder().url(url)
                .timeout(getMcpClientTimeout()).logRequests(false).logResponses(false).build();
        McpClient client = new DefaultMcpClient.Builder().transport(transport)
                .protocolVersion(MCP_GATEWAY_PROTOCOL_VERSION).initializationTimeout(getMcpClientTimeout())
                .autoHealthCheck(false).build();
        try {
            List<ToolSpecification> toolSpecifications = client.listTools();
            List<ToolDefinition> result = ToolSpecificationConverter.convert(toolSpecifications);

            logger.info("成功从审核容器获取到工具列表，共 {} 个工具定义", result != null ? result.size() : 0);
            return result;

        } catch (Exception e) {
            logger.error("从审核容器调用MCP Gateway API失败: {}:{}", containerIp, containerPort, e);
            throw new BusinessException("从审核容器调用MCP Gateway API失败: " + e.getMessage(), e);
        } finally {
            client.close();
        }
    }

    /** 创建配置了超时的HTTP客户端 */
    private CloseableHttpClient createHttpClient() {
        RequestConfig config = RequestConfig.custom().setConnectTimeout(properties.getConnectTimeout())
                .setSocketTimeout(properties.getReadTimeout())
                .setConnectionRequestTimeout(properties.getConnectionRequestTimeout()).build();

        return HttpClients.custom().setDefaultRequestConfig(config).build();
    }

    Duration getMcpClientTimeout() {
        return Duration.ofMillis(properties.getReadTimeout());
    }

}
