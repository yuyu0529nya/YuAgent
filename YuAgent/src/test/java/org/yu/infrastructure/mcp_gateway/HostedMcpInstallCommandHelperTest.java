package org.yu.infrastructure.mcp_gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class HostedMcpInstallCommandHelperTest {

    @Test
    void shouldNormalizeAliyunHostedShorthandConfig() {
        Map<String, Object> shorthandConfig = new HashMap<>();
        shorthandConfig.put("type", "streamableHttp");
        shorthandConfig.put("url", "https://dashscope.aliyuncs.com/api/v1/mcps/market-cmapi033617/mcp");
        shorthandConfig.put("headers", Map.of("Authorization", "Bearer test"));

        HostedMcpInstallCommandHelper.NormalizedInstallCommand normalized = HostedMcpInstallCommandHelper
                .normalizeInstallCommand(Map.of("mcpServers", shorthandConfig), null, "天气预报查询");

        assertTrue(normalized.hostedConfig());
        assertEquals("streamableHttp", normalized.transportType());
        assertNotNull(normalized.serverName());
        assertTrue(normalized.installCommand().containsKey("mcpServers"));
        assertTrue(((Map<?, ?>) normalized.installCommand().get("mcpServers")).containsKey(normalized.serverName()));
    }

    @Test
    void shouldDetectHostedSseConfig() {
        Map<String, Object> serverConfig = new HashMap<>();
        serverConfig.put("type", "sse");
        serverConfig.put("baseUrl", "https://dashscope.aliyuncs.com/api/v1/mcps/weather/sse");
        serverConfig.put("headers", Map.of("Authorization", "Bearer test"));

        Map<String, Object> installCommand = Map.of("mcpServers", Map.of("weather", serverConfig));

        assertTrue(HostedMcpInstallCommandHelper.isHostedConfig(installCommand));
        assertEquals("sse", HostedMcpInstallCommandHelper.getHostedTransportType(installCommand));
    }

    @Test
    void shouldReturnFalseForClassicGatewayConfig() {
        Map<String, Object> serverConfig = new HashMap<>();
        serverConfig.put("command", "npx");
        serverConfig.put("args", java.util.List.of("-y", "@modelcontextprotocol/server-filesystem", "/tmp"));

        Map<String, Object> installCommand = Map.of("mcpServers", Map.of("filesystem", serverConfig));

        assertFalse(HostedMcpInstallCommandHelper.isHostedConfig(installCommand));
    }

    @Test
    void shouldSupportDirectServerConfigWithoutMcpServersWrapper() {
        Map<String, Object> directConfig = new HashMap<>();
        directConfig.put("command", "npx");
        directConfig.put("args", java.util.List.of("-y", "@modelcontextprotocol/server-filesystem", "/tmp"));

        HostedMcpInstallCommandHelper.NormalizedInstallCommand normalized = HostedMcpInstallCommandHelper
                .normalizeInstallCommand(directConfig, "filesystem", "本地文件系统");

        assertEquals("filesystem", normalized.serverName());
        assertEquals("stdio", normalized.transportType());
        assertFalse(normalized.hostedConfig());
    }
}
