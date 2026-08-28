package org.yu.infrastructure.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.yu.infrastructure.exception.BusinessException;
import org.yu.infrastructure.verification.storage.CodeStorage;

class VerificationCodeServiceTest {

    private final CodeStorage storage = new CodeStorage() {
        @Override
        public void storeCode(String key, String code, long expirationMillis) {
        }

        @Override
        public String getCode(String key) {
            return null;
        }

        @Override
        public boolean verifyCode(String key, String code) {
            return false;
        }

        @Override
        public void removeCode(String key) {
        }

        @Override
        public void cleanExpiredCodes() {
        }
    };

    @Test
    void shouldAllowOnlyOneConcurrentQuotaReservationForSameEmail() throws Exception {
        VerificationCodeService service = new VerificationCodeService(storage);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        service.reserveSendQuota("user@example.com", "127.0.0.1", 1_000L);
                        return true;
                    } catch (BusinessException ignored) {
                        return false;
                    }
                }));
            }

            ready.await();
            start.countDown();

            int successfulReservations = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    successfulReservations++;
                }
            }
            assertEquals(1, successfulReservations);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldEnforceDailyEmailLimit() {
        VerificationCodeService service = new VerificationCodeService(storage);
        long now = 1_000L;

        for (int index = 0; index < 10; index++) {
            service.reserveSendQuota("user@example.com", "127.0.0.1", now);
            now += 61_000L;
        }

        long limitReachedAt = now;
        assertThrows(BusinessException.class,
                () -> service.reserveSendQuota("user@example.com", "127.0.0.1", limitReachedAt));
    }
}
