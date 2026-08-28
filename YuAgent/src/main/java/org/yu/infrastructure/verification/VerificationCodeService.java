package org.yu.infrastructure.verification;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.yu.infrastructure.exception.BusinessException;
import org.yu.infrastructure.verification.storage.CodeStorage;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class VerificationCodeService {
    // 业务类型常量
    public static final String BUSINESS_TYPE_REGISTER = "register";
    public static final String BUSINESS_TYPE_RESET_PASSWORD = "reset_password";

    // 存储邮箱和发送次数的映射，用于防刷
    private final Map<String, LimitInfo> limitMap = new ConcurrentHashMap<>();
    // 存储IP和发送次数的映射，用于防刷
    private final Map<String, IpLimitInfo> ipLimitMap = new ConcurrentHashMap<>();
    private final Object rateLimitMonitor = new Object();

    // 验证码存储接口，可以是内存存储或Redis存储
    private final CodeStorage codeStorage;

    // 验证码长度
    private static final int CODE_LENGTH = 6;
    // 验证码有效期（分钟）
    private static final int EXPIRATION_MINUTES = 10;
    // 单个邮箱每日最大发送次数
    private static final int MAX_DAILY_SEND_COUNT = 10;
    // 验证码发送最小间隔（秒）
    private static final int MIN_SEND_INTERVAL_SECONDS = 60;
    // IP每日最大发送次数
    private static final int MAX_DAILY_IP_SEND_COUNT = 20;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    public VerificationCodeService(CodeStorage codeStorage) {
        this.codeStorage = codeStorage;
    }

    /** 生成邮箱验证码
     * @param email 邮箱
     * @param captchaUuid 图形验证码UUID
     * @param captchaCode 用户输入的图形验证码
     * @param ip 用户IP
     * @param businessType 业务类型（注册/重置密码等）
     * @return 生成的验证码 */
    public String generateCode(String email, String captchaUuid, String captchaCode, String ip, String businessType) {
        // 验证图形验证码
        if (!CaptchaUtils.verifyCaptcha(captchaUuid, captchaCode)) {
            throw new BusinessException("图形验证码错误或已过期");
        }

        // 生成6位数字验证码
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(SECURE_RANDOM.nextInt(10));
        }

        // 计算过期时间
        long expirationMillis = TimeUnit.MINUTES.toMillis(EXPIRATION_MINUTES);

        // 检查并占用防刷额度必须是同一个临界区；单独 get/put 在并发请求下会让多个
        // 请求同时通过检查，绕过发送间隔与每日限制。
        reserveSendQuota(email, ip, System.currentTimeMillis());

        // 使用带业务类型的key
        String storageKey = generateStorageKey(email, businessType);
        codeStorage.storeCode(storageKey, code.toString(), expirationMillis);

        return code.toString();
    }

    /** 兼容原有接口，默认使用注册业务类型 */
    public String generateCode(String email, String captchaUuid, String captchaCode, String ip) {
        return generateCode(email, captchaUuid, captchaCode, ip, BUSINESS_TYPE_REGISTER);
    }

    /** 验证验证码
     * @param email 邮箱
     * @param code 验证码
     * @param businessType 业务类型
     * @return 验证结果 */
    public boolean verifyCode(String email, String code, String businessType) {
        String storageKey = generateStorageKey(email, businessType);
        return codeStorage.verifyCode(storageKey, code);
    }

    /** 兼容原有接口，默认使用注册业务类型 */
    public boolean verifyCode(String email, String code) {
        return verifyCode(email, code, BUSINESS_TYPE_REGISTER);
    }

    /** 生成用于存储的key，包含业务类型前缀 */
    private String generateStorageKey(String email, String businessType) {
        return businessType + ":" + email;
    }

    void reserveSendQuota(String email, String ip, long now) {
        synchronized (rateLimitMonitor) {
            LimitInfo limitInfo = limitMap.get(email);
            // 检查发送间隔
            if (limitInfo != null) {
                long elapsedSeconds = TimeUnit.MILLISECONDS.toSeconds(now - limitInfo.getLastSendTime());
                if (elapsedSeconds < MIN_SEND_INTERVAL_SECONDS) {
                    throw new BusinessException("发送过于频繁，请" + (MIN_SEND_INTERVAL_SECONDS - elapsedSeconds) + "秒后再试");
                }

                if (limitInfo.getDailyCount() >= MAX_DAILY_SEND_COUNT) {
                    throw new BusinessException("今日发送次数已达上限，请明天再试");
                }
            }

            IpLimitInfo ipLimitInfo = ipLimitMap.get(ip);
            if (ipLimitInfo != null && ipLimitInfo.getDailyCount() >= MAX_DAILY_IP_SEND_COUNT) {
                throw new BusinessException("您的IP今日请求次数已达上限，请明天再试");
            }

            limitInfo = limitInfo == null ? new LimitInfo() : limitInfo;
            ipLimitInfo = ipLimitInfo == null ? new IpLimitInfo() : ipLimitInfo;
            limitInfo.incrementCount();
            limitInfo.setLastSendTime(now);
            ipLimitInfo.incrementCount();
            limitMap.put(email, limitInfo);
            ipLimitMap.put(ip, ipLimitInfo);
        }
    }

    /** 每日零点重置发送配额，避免“每日”额度永久累积。 */
    @Scheduled(cron = "0 0 0 * * *")
    public void resetAllCounts() {
        synchronized (rateLimitMonitor) {
            limitMap.clear();
            ipLimitMap.clear();
        }
    }

    // 限制信息内部类
    private static class LimitInfo {
        private int dailyCount = 0;
        private long lastSendTime = 0;

        public void incrementCount() {
            dailyCount++;
        }

        public int getDailyCount() {
            return dailyCount;
        }

        public long getLastSendTime() {
            return lastSendTime;
        }

        public void setLastSendTime(long lastSendTime) {
            this.lastSendTime = lastSendTime;
        }
    }

    // IP限制信息内部类
    private static class IpLimitInfo {
        private int dailyCount = 0;

        public void incrementCount() {
            dailyCount++;
        }

        public int getDailyCount() {
            return dailyCount;
        }
    }
}
