package org.yu.infrastructure.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yu.infrastructure.config.OssProperties;
import org.yu.infrastructure.exception.BusinessException;

/** OSS上传服务 提供前端直传OSS的上传凭证生成功能 */
@Service
public class OssUploadService {

    private static final Duration CREDENTIAL_TTL = Duration.ofMinutes(1);
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    private final OssProperties ossProperties;
    private final ObjectMapper objectMapper;

    public OssUploadService(OssProperties ossProperties, ObjectMapper objectMapper) {
        this.ossProperties = ossProperties;
        this.objectMapper = objectMapper;
    }

    /** 生成前端直传OSS的上传凭证
     * 
     * @return 上传凭证信息 */
    public UploadCredential generateUploadCredential() {
        try {
            validateConfiguration();
            String bucketName = resolveBucketName();
            String endpoint = resolveEndpoint();
            String accessKey = resolveAccessKey();
            Instant expirationInstant = Instant.now().plus(CREDENTIAL_TTL);
            Date expiration = Date.from(expirationInstant);

            String datePath = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String keyPrefix = "agent/" + datePath + "/";

            String encodedPolicy = encodePolicy(bucketName, keyPrefix, expirationInstant);
            String signature = generateSignature(encodedPolicy);
            String uploadUrl = buildUploadUrl(bucketName, endpoint);
            String accessUrlPrefix = uploadUrl + "/" + keyPrefix;

            return new UploadCredential(uploadUrl, accessKey, encodedPolicy, signature, keyPrefix, accessUrlPrefix,
                    expiration, MAX_FILE_SIZE_BYTES);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("生成上传凭证失败", e);
        }
    }

    private void validateConfiguration() {
        List<String> missingFields = new ArrayList<>();
        if (!StringUtils.hasText(resolveEndpoint())) {
            missingFields.add("oss.endpoint");
        }
        if (!StringUtils.hasText(resolveAccessKey())) {
            missingFields.add("oss.access-key");
        }
        if (!StringUtils.hasText(resolveSecretKey())) {
            missingFields.add("oss.secret-key");
        }
        if (!StringUtils.hasText(resolveBucketName())) {
            missingFields.add("oss.bucket-name");
        }
        if (!missingFields.isEmpty()) {
            throw new BusinessException("OSS配置缺失，请检查: " + String.join(", ", missingFields));
        }
    }

    private String resolveEndpoint() {
        return firstNonBlank(ossProperties.getEndpoint(), System.getenv("OSS_ENDPOINT"), System.getenv("S3_ENDPOINT"));
    }

    private String resolveAccessKey() {
        return firstNonBlank(ossProperties.getAccessKey(), System.getenv("OSS_ACCESS_KEY"),
                System.getenv("S3_SECRET_ID"));
    }

    private String resolveSecretKey() {
        return firstNonBlank(ossProperties.getSecretKey(), System.getenv("OSS_SECRET_KEY"),
                System.getenv("S3_SECRET_KEY"));
    }

    private String resolveBucketName() {
        return firstNonBlank(ossProperties.getBucketName(), System.getenv("OSS_BUCKET"),
                System.getenv("S3_BUCKET_NAME"));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String encodePolicy(String bucketName, String keyPrefix, Instant expiration) throws Exception {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("expiration", DateTimeFormatter.ISO_INSTANT.format(expiration));
        policy.put("conditions", List.of(Map.of("bucket", bucketName), List.of("starts-with", "$key", keyPrefix),
                List.of("content-length-range", 0, MAX_FILE_SIZE_BYTES)));
        return Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(policy));
    }

    private String buildUploadUrl(String bucketName, String endpoint) {
        String normalizedEndpoint = endpoint.trim().replaceFirst("^https?://", "").replaceAll("/+$", "");
        return "https://" + bucketName + "." + normalizedEndpoint;
    }

    private String generateSignature(String encodedPolicy) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        SecretKeySpec secretKeySpec = new SecretKeySpec(resolveSecretKey().getBytes(StandardCharsets.UTF_8),
                "HmacSHA1");
        mac.init(secretKeySpec);
        byte[] signatureBytes = mac.doFinal(encodedPolicy.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signatureBytes);
    }

    /** 上传凭证类 */
    public static class UploadCredential {
        private final String uploadUrl; // 上传地址
        private final String accessKeyId; // AccessKey ID
        private final String policy; // Base64编码的Policy
        private final String signature; // 签名
        private final String keyPrefix; // 对象键前缀
        private final String accessUrlPrefix; // 访问URL前缀
        private final Date expiration; // 过期时间
        private final long maxFileSize; // 最大文件大小

        public UploadCredential(String uploadUrl, String accessKeyId, String policy, String signature, String keyPrefix,
                String accessUrlPrefix, Date expiration, long maxFileSize) {
            this.uploadUrl = uploadUrl;
            this.accessKeyId = accessKeyId;
            this.policy = policy;
            this.signature = signature;
            this.keyPrefix = keyPrefix;
            this.accessUrlPrefix = accessUrlPrefix;
            this.expiration = expiration;
            this.maxFileSize = maxFileSize;
        }

        // Getters
        public String getUploadUrl() {
            return uploadUrl;
        }
        public String getAccessKeyId() {
            return accessKeyId;
        }
        public String getPolicy() {
            return policy;
        }
        public String getSignature() {
            return signature;
        }
        public String getKeyPrefix() {
            return keyPrefix;
        }
        public String getAccessUrlPrefix() {
            return accessUrlPrefix;
        }
        public Date getExpiration() {
            return expiration;
        }
        public long getMaxFileSize() {
            return maxFileSize;
        }
    }

}
