package org.yu.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.yu.infrastructure.terminal.TerminalWebSocketHandler;
import org.yu.infrastructure.terminal.TerminalHandshakeInterceptor;

import java.util.Arrays;

/** WebSocket配置 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);
    private final TerminalWebSocketHandler terminalWebSocketHandler;
    private final TerminalHandshakeInterceptor terminalHandshakeInterceptor;
    private final String[] allowedOrigins;

    public WebSocketConfig(TerminalWebSocketHandler terminalWebSocketHandler,
            TerminalHandshakeInterceptor terminalHandshakeInterceptor,
            @Value("${cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}") String allowedOrigins) {
        this.terminalWebSocketHandler = terminalWebSocketHandler;
        this.terminalHandshakeInterceptor = terminalHandshakeInterceptor;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(",")).map(String::trim)
                .filter(origin -> !origin.isEmpty()).toArray(String[]::new);
        logger.info("WebSocket配置初始化完成");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        logger.info("注册WebSocket处理器: /ws/terminal");
        registry.addHandler(terminalWebSocketHandler, "/ws/terminal").addInterceptors(terminalHandshakeInterceptor)
                .setAllowedOrigins(allowedOrigins);
        logger.info("WebSocket处理器注册完成");
    }
}
