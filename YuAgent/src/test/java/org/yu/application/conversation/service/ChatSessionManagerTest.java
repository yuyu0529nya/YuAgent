package org.yu.application.conversation.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ChatSessionManagerTest {

    @Test
    void completingAnOlderEmitterDoesNotRemoveTheReplacementSession() {
        ChatSessionManager manager = new ChatSessionManager();
        SseEmitter olderEmitter = new SseEmitter();
        SseEmitter replacementEmitter = new SseEmitter();

        ChatSessionManager.SessionInfo olderSession = manager.registerSession("session-1", olderEmitter);
        manager.registerSession("session-1", replacementEmitter);

        manager.removeSessionIfMatches("session-1", olderSession);

        assertTrue(manager.hasSession("session-1"));
    }

    @Test
    void interruptionKeepsTheStopMarkerAfterRemovingTheActiveSession() {
        ChatSessionManager manager = new ChatSessionManager();
        manager.registerSession("session-1", new SseEmitter());

        assertTrue(manager.interruptSession("session-1"));
        assertFalse(manager.hasSession("session-1"));
        assertTrue(manager.isSessionInterrupted("session-1"));
    }
}
