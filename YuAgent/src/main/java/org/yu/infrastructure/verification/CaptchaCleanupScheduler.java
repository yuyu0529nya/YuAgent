package org.yu.infrastructure.verification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Removes expired in-memory captchas even when no later verification request arrives. */
@Component
public class CaptchaCleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(CaptchaCleanupScheduler.class);

    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void cleanExpiredCaptchas() {
        int removedCount = CaptchaUtils.cleanExpiredCaptchas();
        if (removedCount > 0) {
            logger.debug("清理过期图形验证码: count={}", removedCount);
        }
    }
}
