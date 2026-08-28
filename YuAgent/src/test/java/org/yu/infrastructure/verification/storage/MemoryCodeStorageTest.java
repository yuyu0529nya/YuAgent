package org.yu.infrastructure.verification.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

class MemoryCodeStorageTest {

    @Test
    void shouldConsumeCodeOnlyOnceWhenVerifiedConcurrently() throws Exception {
        MemoryCodeStorage storage = new MemoryCodeStorage();
        storage.storeCode("register:user@example.com", "123456", 60_000);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return storage.verifyCode("register:user@example.com", "123456");
                }));
            }

            ready.await();
            start.countDown();

            int successfulVerifications = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    successfulVerifications++;
                }
            }
            assertEquals(1, successfulVerifications);
        } finally {
            executor.shutdownNow();
            storage.destroy();
        }
    }

    @Test
    void shouldKeepCodeAfterIncorrectAttemptAndRejectExpiredCode() {
        MemoryCodeStorage storage = new MemoryCodeStorage();
        try {
            storage.storeCode("reset:user@example.com", "123456", 60_000);
            assertFalse(storage.verifyCode("reset:user@example.com", "000000"));
            assertTrue(storage.verifyCode("reset:user@example.com", "123456"));

            storage.storeCode("expired:user@example.com", "123456", 0);
            assertFalse(storage.verifyCode("expired:user@example.com", "123456"));
        } finally {
            storage.destroy();
        }
    }
}
