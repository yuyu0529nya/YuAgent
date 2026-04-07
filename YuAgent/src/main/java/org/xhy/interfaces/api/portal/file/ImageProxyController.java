package org.xhy.interfaces.api.portal.file;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.xhy.infrastructure.config.OssProperties;
import org.xhy.infrastructure.exception.BusinessException;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@RestController
@RequestMapping("/files")
public class ImageProxyController {

    private final OssProperties ossProperties;

    public ImageProxyController(OssProperties ossProperties) {
        this.ossProperties = ossProperties;
    }

    @GetMapping("/image-proxy")
    public ResponseEntity<byte[]> proxyImage(@RequestParam("url") String url) {
        if (!StringUtils.hasText(url)) {
            throw new BusinessException("图片地址不能为空");
        }

        URI uri = parseUrl(url);
        validateImageRequest(uri, url);

        String objectKey = extractObjectKey(uri);
        ResponseBytes<GetObjectResponse> responseBytes = downloadObject(objectKey);
        MediaType mediaType = resolveMediaType(responseBytes.response().contentType(), url);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(mediaType)
                .body(responseBytes.asByteArray());
    }

    private URI parseUrl(String url) {
        try {
            return URI.create(url);
        } catch (Exception e) {
            throw new BusinessException("图片地址格式不正确", e);
        }
    }

    private void validateImageRequest(URI uri, String url) {
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new BusinessException("仅支持代理 HTTP/HTTPS 图片");
        }

        String host = uri.getHost();
        if (!StringUtils.hasText(host) || !isAllowedHost(host)) {
            throw new BusinessException("图片地址不在允许的存储域名范围内");
        }

        MediaType mediaType = resolveMediaType(null, url);
        if (!"image".equalsIgnoreCase(mediaType.getType())) {
            throw new BusinessException("仅支持图片预览");
        }
    }

    private boolean isAllowedHost(String host) {
        String lowerHost = host.toLowerCase(Locale.ROOT);
        for (String allowedHost : allowedHosts()) {
            if (lowerHost.equals(allowedHost) || lowerHost.endsWith("." + allowedHost)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> allowedHosts() {
        Set<String> hosts = new LinkedHashSet<>();
        addHost(hosts, resolveEndpoint());
        addHost(hosts, ossProperties.getCustomDomain());
        addHost(hosts, ossProperties.getUrlPrefix());
        hosts.add("aliyuncs.com");
        hosts.add("myqcloud.com");
        return hosts;
    }

    private void addHost(Set<String> hosts, String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return;
        }

        String candidate = rawValue.trim();
        if (!candidate.contains("://")) {
            candidate = "https://" + candidate;
        }

        try {
            URI uri = URI.create(candidate);
            if (StringUtils.hasText(uri.getHost())) {
                hosts.add(uri.getHost().toLowerCase(Locale.ROOT));
            }
        } catch (Exception ignored) {
        }
    }

    private String extractObjectKey(URI uri) {
        String path = uri.getPath();
        if (!StringUtils.hasText(path)) {
            throw new BusinessException("图片对象路径不能为空");
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private ResponseBytes<GetObjectResponse> downloadObject(String objectKey) {
        String endpoint = resolveEndpoint();
        String accessKey = resolveAccessKey();
        String secretKey = resolveSecretKey();
        String bucketName = resolveBucketName();
        String region = StringUtils.hasText(ossProperties.getRegion()) ? ossProperties.getRegion() : "cn-beijing";

        if (!StringUtils.hasText(endpoint) || !StringUtils.hasText(accessKey) || !StringUtils.hasText(secretKey)
                || !StringUtils.hasText(bucketName)) {
            throw new BusinessException("图片代理所需的对象存储配置不完整");
        }

        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(ossProperties.isPathStyleAccess())
                .build();

        try (S3Client s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .serviceConfiguration(serviceConfiguration)
                .credentialsProvider(StaticCredentialsProvider
                        .create(AwsBasicCredentials.create(accessKey, secretKey)))
                .build()) {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();
            return s3Client.getObjectAsBytes(request);
        } catch (Exception e) {
            throw new BusinessException("读取私有图片失败", e);
        }
    }

    private MediaType resolveMediaType(String contentType, String url) {
        if (StringUtils.hasText(contentType)) {
            try {
                return MediaType.parseMediaType(contentType);
            } catch (Exception ignored) {
            }
        }

        String lowerUrl = url.toLowerCase(Locale.ROOT);
        if (lowerUrl.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (lowerUrl.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (lowerUrl.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        if (lowerUrl.endsWith(".bmp")) {
            return MediaType.parseMediaType("image/bmp");
        }
        if (lowerUrl.endsWith(".svg")) {
            return MediaType.parseMediaType("image/svg+xml");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private String resolveEndpoint() {
        return firstNonBlank(ossProperties.getEndpoint(), System.getenv("OSS_ENDPOINT"), System.getenv("S3_ENDPOINT"));
    }

    private String resolveAccessKey() {
        return firstNonBlank(ossProperties.getAccessKey(), System.getenv("OSS_ACCESS_KEY"), System.getenv("S3_SECRET_ID"));
    }

    private String resolveSecretKey() {
        return firstNonBlank(ossProperties.getSecretKey(), System.getenv("OSS_SECRET_KEY"), System.getenv("S3_SECRET_KEY"));
    }

    private String resolveBucketName() {
        return firstNonBlank(ossProperties.getBucketName(), System.getenv("OSS_BUCKET"), System.getenv("S3_BUCKET_NAME"));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
