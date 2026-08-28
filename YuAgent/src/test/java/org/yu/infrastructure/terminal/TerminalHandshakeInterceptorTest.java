package org.yu.infrastructure.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.yu.domain.user.model.UserEntity;
import org.yu.domain.user.service.UserDomainService;
import org.yu.infrastructure.utils.JwtUtils;

class TerminalHandshakeInterceptorTest {

    @Test
    void shouldAllowAuthenticatedAdministrator() throws Exception {
        UserDomainService userDomainService = mock(UserDomainService.class);
        TerminalHandshakeInterceptor interceptor = new TerminalHandshakeInterceptor(userDomainService);
        String token = JwtUtils.generateToken("admin-id");
        UserEntity admin = new UserEntity();
        admin.setIsAdmin(true);
        when(userDomainService.getUserInfo("admin-id")).thenReturn(admin);
        ServerHttpRequest request = requestWithTokenCookie(token);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Map<String, Object> attributes = new HashMap<>();

        assertTrue(interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), attributes));
        assertEquals("admin-id", attributes.get(TerminalHandshakeInterceptor.USER_ID_ATTRIBUTE));
    }

    @Test
    void shouldRejectMissingToken() throws Exception {
        TerminalHandshakeInterceptor interceptor = new TerminalHandshakeInterceptor(mock(UserDomainService.class));
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(new URI("ws://localhost/api/ws/terminal?containerId=container-id"));
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        assertFalse(interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), new HashMap<>()));
        org.mockito.Mockito.verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectTokenProvidedOnlyInQueryString() throws Exception {
        TerminalHandshakeInterceptor interceptor = new TerminalHandshakeInterceptor(mock(UserDomainService.class));
        String token = JwtUtils.generateToken("admin-id");
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI())
                .thenReturn(new URI("ws://localhost/api/ws/terminal?containerId=container-id&token=" + token));
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        assertFalse(interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), new HashMap<>()));
        org.mockito.Mockito.verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    private ServerHttpRequest requestWithTokenCookie(String token) throws Exception {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(new URI("ws://localhost/api/ws/terminal?containerId=container-id"));
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8));
        when(request.getHeaders()).thenReturn(headers);
        return request;
    }
}
