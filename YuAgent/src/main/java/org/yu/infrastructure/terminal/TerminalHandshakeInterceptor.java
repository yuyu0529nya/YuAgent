package org.yu.infrastructure.terminal;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.yu.domain.user.model.UserEntity;
import org.yu.domain.user.service.UserDomainService;
import org.yu.infrastructure.utils.JwtUtils;

/** 为容器终端 WebSocket 执行登录与管理员权限校验。 */
@Component
public class TerminalHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID_ATTRIBUTE = "terminalUserId";
    private static final String AUTH_COOKIE_NAME = "token";

    private final UserDomainService userDomainService;

    public TerminalHandshakeInterceptor(UserDomainService userDomainService) {
        this.userDomainService = userDomainService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        String token = getTokenFromCookie(request);
        if (!JwtUtils.validateToken(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        String userId = JwtUtils.getUserIdFromToken(token);
        UserEntity user = userDomainService.getUserInfo(userId);
        if (user == null || !user.isAdmin()) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        attributes.put(USER_ID_ATTRIBUTE, userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
            Exception exception) {
        // 无需额外处理。
    }

    private String getTokenFromCookie(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        if (headers == null) {
            return null;
        }

        String cookieHeader = headers.getFirst(HttpHeaders.COOKIE);
        if (cookieHeader == null) {
            return null;
        }

        for (String cookie : cookieHeader.split(";")) {
            String trimmedCookie = cookie.trim();
            int separatorIndex = trimmedCookie.indexOf('=');
            if (separatorIndex <= 0 || !AUTH_COOKIE_NAME.equals(trimmedCookie.substring(0, separatorIndex))) {
                continue;
            }

            try {
                return URLDecoder.decode(trimmedCookie.substring(separatorIndex + 1), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        return null;
    }
}
