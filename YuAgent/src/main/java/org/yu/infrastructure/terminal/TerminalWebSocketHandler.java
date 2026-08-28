package org.yu.infrastructure.terminal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/** 终端WebSocket处理器 */
@Component
public class TerminalWebSocketHandler implements WebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(TerminalWebSocketHandler.class);

    private final WebTerminalService webTerminalService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TerminalWebSocketHandler(WebTerminalService webTerminalService) {
        this.webTerminalService = webTerminalService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("WebSocket连接建立: {}", session.getId());

        // 握手拦截器已经验证令牌与管理员权限。
        if (!session.getAttributes().containsKey(TerminalHandshakeInterceptor.USER_ID_ATTRIBUTE)) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("未授权的终端连接"));
            return;
        }

        // 从URL参数中获取容器ID
        URI uri = session.getUri();
        if (uri != null) {
            String containerId = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("containerId");

            if (containerId != null) {
                // 创建终端会话
                boolean success = webTerminalService.createTerminalSession(session.getId(), containerId, session);
                if (!success) {
                    session.close(CloseStatus.SERVER_ERROR.withReason("无法创建终端会话"));
                }
            } else {
                session.close(CloseStatus.BAD_DATA.withReason("缺少容器ID参数"));
            }
        } else {
            session.close(CloseStatus.BAD_DATA.withReason("无效的连接参数"));
        }
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        if (message instanceof TextMessage) {
            TextMessage textMessage = (TextMessage) message;
            String payload = textMessage.getPayload();

            try {
                JsonNode jsonNode = objectMapper.readTree(payload);
                if (!jsonNode.isObject()) {
                    logger.warn("忽略非对象终端消息: sessionId={}", session.getId());
                    return;
                }

                String type = jsonNode.path("type").asText();

                if ("input".equals(type)) {
                    JsonNode inputNode = jsonNode.get("data");
                    if (inputNode == null || !inputNode.isTextual()) {
                        logger.warn("忽略缺少文本输入的终端消息: sessionId={}", session.getId());
                        return;
                    }
                    String input = inputNode.asText();
                    webTerminalService.sendCommand(session.getId(), input);
                } else if ("resize".equals(type)) {
                    logger.debug("终端大小调整: sessionId={}", session.getId());
                } else {
                    logger.warn("忽略未知终端消息类型: sessionId={}, type={}", session.getId(), type);
                }
            } catch (JsonProcessingException e) {
                // 兼容早期客户端直接发送纯文本命令；结构化消息的字段错误不会落入这里。
                webTerminalService.sendCommand(session.getId(), payload);
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket传输错误: {}", session.getId(), exception);
        webTerminalService.closeTerminalSession(session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        logger.info("WebSocket连接关闭: {} - {}", session.getId(), closeStatus);
        webTerminalService.closeTerminalSession(session.getId());
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
