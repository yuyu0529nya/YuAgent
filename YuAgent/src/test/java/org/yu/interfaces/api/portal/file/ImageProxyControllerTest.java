package org.yu.interfaces.api.portal.file;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.yu.infrastructure.config.OssProperties;
import org.yu.infrastructure.exception.BusinessException;

class ImageProxyControllerTest {

    private final ImageProxyController controller = new ImageProxyController(properties());

    @Test
    void shouldAcceptConfiguredBucketImageWithSignedQueryParameters() {
        assertDoesNotThrow(() -> controller.validateImageRequest(
                URI.create("https://assets.oss-cn-beijing.aliyuncs.com/avatars/user.png?Expires=123&Signature=abc")));
    }

    @Test
    void shouldRejectOtherStorageHostsEvenWhenTheyUseTheSameProviderDomain() {
        assertThrows(BusinessException.class, () -> controller
                .validateImageRequest(URI.create("https://other-bucket.oss-cn-beijing.aliyuncs.com/avatars/user.png")));
    }

    @Test
    void shouldRejectSubdomainsOfTheConfiguredBucketHost() {
        assertThrows(BusinessException.class, () -> controller.validateImageRequest(
                URI.create("https://attacker.assets.oss-cn-beijing.aliyuncs.com/avatars/user.png")));
    }

    @Test
    void shouldAcceptConfiguredCustomDomain() {
        assertDoesNotThrow(
                () -> controller.validateImageRequest(URI.create("https://cdn.example.com/avatars/user.webp")));
    }

    private OssProperties properties() {
        OssProperties properties = new OssProperties();
        properties.setEndpoint("https://oss-cn-beijing.aliyuncs.com");
        properties.setBucketName("assets");
        properties.setCustomDomain("https://cdn.example.com");
        return properties;
    }
}
