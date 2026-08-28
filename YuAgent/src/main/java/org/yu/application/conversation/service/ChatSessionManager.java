package org.yu.application.conversation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.yu.infrastructure.transport.SseEmitterUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Manages active chat sessions and interruption state. */
@Component
public class ChatSessionManager {

    private static final Logger logger = LoggerFactory.getLogger(ChatSessionManager.class);
    private static final long INTERRUPTED_SESSION_TTL_MILLIS = 30 * 60 * 1000L;

    /** Runtime state for one active session. */
    public static class SessionInfo {
        private final String sessionId;
        private final SseEmitter emitter;
        private final AtomicBoolean interrupted;
        private final long startTime;

        public SessionInfo(String sessionId, SseEmitter emitter) {
            this.sessionId = sessionId;
            this.emitter = emitter;
            this.interrupted = new AtomicBoolean(false);
            this.startTime = System.currentTimeMillis();
        }

        public String getSessionId() {
            return sessionId;
        }

        public SseEmitter getEmitter() {
            return emitter;
        }

        public boolean isInterrupted() {
            return interrupted.get();
        }

        public void setInterrupted() {
            interrupted.set(true);
        }

        public long getStartTime() {
            return startTime;
        }
    }

    private final ConcurrentHashMap<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> interruptedSessions = new ConcurrentHashMap<>();

    public SessionInfo registerSession(String sessionId, SseEmitter emitter) {
        interruptedSessions.remove(sessionId);

        SessionInfo sessionInfo = new SessionInfo(sessionId, emitter);
        activeSessions.put(sessionId, sessionInfo);
        logger.info("Registered chat session: sessionId={}", sessionId);

        emitter.onCompletion(() -> {
            removeSessionIfMatches(sessionId, sessionInfo);
            logger.info("Chat session completed: sessionId={}", sessionId);
        });

        emitter.onTimeout(() -> {
            removeSessionIfMatches(sessionId, sessionInfo);
            logger.warn("Chat session timed out: sessionId={}", sessionId);
        });

        emitter.onError((throwable) -> {
            removeSessionIfMatches(sessionId, sessionInfo);
            logger.error("Chat session error: sessionId={}, error={}", sessionId, throwable.getMessage());
        });

        return sessionInfo;
    }

    public void removeSession(String sessionId) {
        removeSessionIfMatches(sessionId, null);
    }

    void removeSessionIfMatches(String sessionId, SessionInfo expectedSession) {
        SessionInfo removed = expectedSession == null
                ? activeSessions.remove(sessionId)
                : activeSessions.remove(sessionId, expectedSession) ? expectedSession : null;
        if (removed != null) {
            if (!removed.isInterrupted()) {
                interruptedSessions.remove(sessionId);
            }
            long duration = System.currentTimeMillis() - removed.getStartTime();
            logger.info("Removed chat session: sessionId={}, duration={}ms", sessionId, duration);
        }
    }

    public boolean interruptSession(String sessionId) {
        SessionInfo sessionInfo = activeSessions.get(sessionId);
        if (sessionInfo == null) {
            logger.warn("Tried to interrupt a missing session: sessionId={}", sessionId);
            return false;
        }

        if (!activeSessions.remove(sessionId, sessionInfo)) {
            logger.warn("Chat session changed before interruption could be applied: sessionId={}", sessionId);
            return false;
        }

        sessionInfo.setInterrupted();
        interruptedSessions.put(sessionId, System.currentTimeMillis());
        logger.info("Marked chat session as interrupted: sessionId={}", sessionId);

        try {
            SseEmitter emitter = sessionInfo.getEmitter();
            SseEmitterUtils.safeSend(emitter,
                    SseEmitter.event().name("interrupt").data("{\"interrupted\": true, \"message\": \"对话已被中断\"}"));
            SseEmitterUtils.safeComplete(emitter);
            logger.info("Chat session interrupted: sessionId={}", sessionId);
            return true;
        } catch (Exception e) {
            logger.error("Error while interrupting chat session: sessionId={}, error={}", sessionId, e.getMessage());
            return true;
        }
    }

    public boolean isSessionInterrupted(String sessionId) {
        SessionInfo sessionInfo = activeSessions.get(sessionId);
        if (sessionInfo != null && sessionInfo.isInterrupted()) {
            return true;
        }

        Long interruptedAt = interruptedSessions.get(sessionId);
        if (interruptedAt == null) {
            return false;
        }
        if (interruptedAt <= System.currentTimeMillis() - INTERRUPTED_SESSION_TTL_MILLIS) {
            interruptedSessions.remove(sessionId, interruptedAt);
            return false;
        }
        return true;
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    void cleanupExpiredInterruptedSessions() {
        long expirationThreshold = System.currentTimeMillis() - INTERRUPTED_SESSION_TTL_MILLIS;
        interruptedSessions.entrySet().removeIf(entry -> entry.getValue() <= expirationThreshold);
    }

    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    public boolean hasSession(String sessionId) {
        return activeSessions.containsKey(sessionId);
    }
}
