package org.yu.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yu.infrastructure.config.OssProperties;

class OssUploadServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCreateAnUtf8PolicyWithOneMinuteUtcExpiry() throws Exception {
        Instant before = Instant.now();
        OssUploadService.UploadCredential credential = service().generateUploadCredential();
        Instant after = Instant.now();

        Map<String, Object> policy = objectMapper.readValue(Base64.getDecoder().decode(credential.getPolicy()),
                new TypeReference<>() {
                });
        Instant expiration = Instant.parse((String) policy.get("expiration"));

        assertEquals("https://assets.oss-cn-beijing.aliyuncs.com", credential.getUploadUrl());
        assertEquals(10L * 1024 * 1024, credential.getMaxFileSize());
        assertTrue(!expiration.isBefore(before.plusSeconds(59)));
        assertTrue(!expiration.isAfter(after.plus(Duration.ofSeconds(61))));
        assertEquals(List.of("content-length-range", 0, 10 * 1024 * 1024),
                ((List<?>) ((List<?>) policy.get("conditions")).get(2)));
    }

    private OssUploadService service() {
        OssProperties properties = new OssProperties();
        properties.setEndpoint("https://oss-cn-beijing.aliyuncs.com");
        properties.setAccessKey("access-key");
        properties.setSecretKey("secret-key");
        properties.setBucketName("assets");
        return new OssUploadService(properties, objectMapper);
    }
}
