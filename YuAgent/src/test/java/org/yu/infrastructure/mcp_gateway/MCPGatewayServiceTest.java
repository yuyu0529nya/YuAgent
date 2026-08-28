package org.yu.infrastructure.mcp_gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.yu.infrastructure.config.MCPGatewayProperties;

class MCPGatewayServiceTest {

    @Test
    void usesTheConfiguredReadTimeoutForMcpRequests() {
        MCPGatewayProperties properties = new MCPGatewayProperties();
        properties.setReadTimeout(45_000);

        MCPGatewayService service = new MCPGatewayService(properties);
        try {
            assertEquals(Duration.ofSeconds(45), service.getMcpClientTimeout());
        } finally {
            service.close();
        }
    }
}
