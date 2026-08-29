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

    @Test
    void buildsStreamableHttpEndpointForUserContainer() {
        MCPGatewayProperties properties = new MCPGatewayProperties();
        properties.setApiKey("secret");

        MCPGatewayService service = new MCPGatewayService(properties);
        try {
            assertEquals("http://10.0.0.5:8080/weather?api_key=secret",
                    service.buildUserContainerUrl("weather", "10.0.0.5", 8080));
        } finally {
            service.close();
        }
    }

    @Test
    void buildsGlobalStreamableHttpEndpoint() {
        MCPGatewayProperties properties = new MCPGatewayProperties();
        properties.setBaseUrl("http://gateway:8080");
        properties.setApiKey("secret");

        MCPGatewayService service = new MCPGatewayService(properties);
        try {
            assertEquals("http://gateway:8080/stream?api_key=secret", service.buildGlobalSSEUrl("weather"));
        } finally {
            service.close();
        }
    }
}
