package org.yu.infrastructure.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModelResponseToJsonUtilsTest {

    @Test
    void returnsNullWhenModelResponseDoesNotContainJson() {
        assertNull(ModelResponseToJsonUtils.toJson("I need more details before proceeding.", Response.class));
    }

    @Test
    void parsesJsonWrappedInAMarkdownFence() {
        Response response = ModelResponseToJsonUtils.toJson("```json\n{\"message\":\"ok\"}\n```", Response.class);

        assertEquals("ok", response.message);
    }

    public static class Response {
        public String message;
    }
}
