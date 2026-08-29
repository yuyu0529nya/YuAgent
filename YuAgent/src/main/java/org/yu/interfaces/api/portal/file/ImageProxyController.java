package org.yu.interfaces.api.portal.file;

import jakarta.annotation.PreDestroy;
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
import org.yu.infrastructure.config.OssProperties;
import org.yu.infrastructure.exception.BusinessException;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.util.IOUtils;

@RestController
@RequestMapping("/files")
public class ImageProxyController {

    private final OssProperties ossProperties;
    private final Object s3ClientMonitor = new Object();
    private volatile AmazonS3 s3Client;

    public ImageProxyController(OssProperties ossProperties) {
        this.ossProperties = ossProperties;
    }

    @GetMapping("/image-proxy")
    public ResponseEntity<byte[]> proxyImage(@RequestParam("url") String url) {
        if (!StringUtils.hasText(url)) {
            throw new BusinessException("图片地址不能为空");
        }

        URI uri = parseUrl(url);
        validateImageRequest(uri);

        String objectKey = extractObjectKey(uri);
        DownloadedObject downloaded = downloadObject(objectKey);
        MediaType mediaType = resolveMediaType(downloaded.contentType(), uri);
        if (!"image".equalsIgnoreCase(mediaType.getType())) {
            throw new BusinessException("对象不是图片类型");
        }

        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline").contentType(mediaType)
                .body(downloaded.content());
    }

    private record DownloadedObject(String contentType, byte[] content) {
    }

    private URI parseUrl(String url) {
        try {
            return URI.create(url);
        } catch (Exception e) {
            throw new BusinessException("图片地址格式不正确", e);
        }
    }

    void validateImageRequest(URI uri) {
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new BusinessException("仅支持代理 HTTP/HTTPS 图片");
        }

        String host = uri.getHost();
        if (!StringUtils.hasText(host) || !isAllowedHost(host)) {
            throw new BusinessException("图片地址不在允许的存储域名范围内");
        }

        MediaType mediaType = resolveMediaType(null, uri);
        if (!"image".equalsIgnoreCase(mediaType.getType())) {
            throw new BusinessException("仅支持图片预览");
        }
    }

    private boolean isAllowedHost(String host) {
        String lowerHost = host.toLowerCase(Locale.ROOT);
        for (String allowedHost : allowedHosts()) {
            if (lowerHost.equals(allowedHost)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> allowedHosts() {
        Set<String> hosts = new LinkedHashSet<>();
        String endpoint = resolveEndpoint();
        addHost(hosts, endpoint);
        addBucketHost(hosts, endpoint, resolveBucketName());
        addHost(hosts, ossProperties.getCustomDomain());
        addHost(hosts, ossProperties.getUrlPrefix());
        return hosts;
    }

    private void addBucketHost(Set<String> hosts, String endpoint, String bucketName) {
        if (!StringUtils.hasText(endpoint) || !StringUtils.hasText(bucketName)) {
            return;
        }

        String candidate = endpoint.contains("://") ? endpoint : "https://" + endpoint;
        try {
            URI uri = URI.create(candidate);
            if (StringUtils.hasText(uri.getHost())) {
                hosts.add(bucketName.toLowerCase(Locale.ROOT) + "." + uri.getHost().toLowerCase(Locale.ROOT));
            }
        } catch (Exception ignored) {
        }
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

    private DownloadedObject downloadObject(String objectKey) {
        String endpoint = resolveEndpoint();
        String accessKey = resolveAccessKey();
        String secretKey = resolveSecretKey();
        String bucketName = resolveBucketName();
        String region = StringUtils.hasText(ossProperties.getRegion()) ? ossProperties.getRegion() : "cn-beijing";

        if (!StringUtils.hasText(endpoint) || !StringUtils.hasText(accessKey) || !StringUtils.hasText(secretKey)
                || !StringUtils.hasText(bucketName)) {
            throw new BusinessException("图片代理所需的对象存储配置不完整");
        }

        try {
            AmazonS3 client = getOrCreateS3Client(endpoint, region, accessKey, secretKey);
            ensureObjectSize(client, bucketName, objectKey);
            S3Object object = client.getObject(new GetObjectRequest(bucketName, objectKey));
            ObjectMetadata metadata = object.getObjectMetadata();
            return new DownloadedObject(metadata.getContentType(), IOUtils.toByteArray(object.getObjectContent()));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("读取私有图片失败", e);
        }
    }

    private void ensureObjectSize(AmazonS3 client, String bucketName, String objectKey) {
        ObjectMetadata metadata = client.getObjectMetadata(bucketName, objectKey);
        long maxSize = ossProperties.getImageProxyMaxSize();
        if (maxSize <= 0) {
            throw new BusinessException("图片代理最大文件大小必须大于 0");
        }
        if (metadata.getContentLength() > maxSize) {
            throw new BusinessException("图片大小超过代理限制");
        }
    }

    private AmazonS3 getOrCreateS3Client(String endpoint, String region, String accessKey, String secretKey) {
        AmazonS3 current = s3Client;
        if (current != null) {
            return current;
        }

        synchronized (s3ClientMonitor) {
            if (s3Client == null) {
                s3Client = AmazonS3ClientBuilder.standard()
                        .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, region))
                        .withPathStyleAccessEnabled(ossProperties.isPathStyleAccess())
                        .withCredentials(
                                new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
                        .build();
            }
            return s3Client;
        }
    }

    @PreDestroy
    public void closeS3Client() {
        AmazonS3 current = s3Client;
        if (current != null) {
            current.shutdown();
        }
    }

    private MediaType resolveMediaType(String contentType, URI uri) {
        if (StringUtils.hasText(contentType)) {
            try {
                return MediaType.parseMediaType(contentType);
            } catch (Exception ignored) {
            }
        }

        String path = uri.getPath();
        String lowerUrl = path == null ? "" : path.toLowerCase(Locale.ROOT);
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
}
