package org.yu.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.yu.interfaces.api.common.Result;

class GlobalExceptionHandlerTest {

    @Test
    void doesNotExposeUnexpectedExceptionDetailsToClients() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost/api/test"));

        Result<Void> result = new GlobalExceptionHandler()
                .handleException(new IllegalStateException("database credentials are invalid"), request);

        assertEquals(500, result.getCode());
        assertEquals("服务器内部错误，请稍后重试", result.getMessage());
    }
}
