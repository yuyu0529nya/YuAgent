package org.yu.application.conversation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.yu.domain.conversation.model.SessionEntity;
import org.yu.domain.conversation.service.SessionDomainService;

class RagSessionManagerTest {

    @Test
    void concurrentRequestsForOneUserCreateOnlyOneSession() throws Exception {
        SessionDomainService sessionDomainService = Mockito.mock(SessionDomainService.class);
        AtomicInteger sequence = new AtomicInteger();
        when(sessionDomainService.createSession(anyString(), anyString())).thenAnswer(invocation -> {
            Thread.sleep(50);
            SessionEntity session = new SessionEntity();
            session.setId("session-" + sequence.incrementAndGet());
            return session;
        });
        RagSessionManager manager = new RagSessionManager(sessionDomainService);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return manager.createOrGetRagSession("user-1");
                }));
            }

            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();
            for (Future<String> future : futures) {
                assertEquals("session-1", future.get(2, TimeUnit.SECONDS));
            }

            verify(sessionDomainService, times(1)).createSession("system-rag-agent", "user-1");
            verify(sessionDomainService, times(1)).updateSession("session-1", "user-1", "RAG对话");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void regularAndUserRagKeysCannotCollide() {
        SessionDomainService sessionDomainService = Mockito.mock(SessionDomainService.class);
        AtomicInteger sequence = new AtomicInteger();
        when(sessionDomainService.createSession(anyString(), anyString())).thenAnswer(invocation -> {
            SessionEntity session = new SessionEntity();
            session.setId("session-" + sequence.incrementAndGet());
            return session;
        });
        RagSessionManager manager = new RagSessionManager(sessionDomainService);

        String regularSession = manager.createOrGetRagSession("user_rag");
        String userRagSession = manager.createOrGetUserRagSession("user", "rag");

        assertNotEquals(regularSession, userRagSession);
        assertEquals(2, manager.getCachedSessionCount());
        verify(sessionDomainService, times(2)).createSession(eq("system-rag-agent"), anyString());
    }
}
