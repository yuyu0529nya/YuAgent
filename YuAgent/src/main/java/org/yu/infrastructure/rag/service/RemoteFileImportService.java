package org.yu.infrastructure.rag.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yu.infrastructure.exception.BusinessException;

@Service
public class RemoteFileImportService {

    private static final int MAX_REDIRECTS = 3;
    private static final List<String> SUPPORTED_EXTENSIONS =
            List.of("pdf", "doc", "docx", "txt", "md", "html", "json", "csv", "xlsx", "xls");
    private static final Pattern HTML_DOWNLOAD_LINK_PATTERN = Pattern.compile(
            "(?:iframe|embed|object|a)[^>]+(?:src|href)=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DIRECT_PDF_PATTERN = Pattern.compile(
            "https?://[^\"'\\s>]+\\.pdf(?:\\?[^\"'\\s>]*)?",
            Pattern.CASE_INSENSITIVE);
    private static final List<String> PROTECTED_HTML_MARKERS = List.of(
            "please enable javascript to view the page content",
            "your support id is",
            "/tspd/",
            "cf-browser-verification",
            "challenge-platform");

    private final HttpClient httpClient;

    @Value("${rag.remote-import.read-timeout:2m}")
    private Duration readTimeout;

    @Value("${rag.remote-import.max-file-size:104857600}")
    private long maxFileSize;

    public RemoteFileImportService() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public DownloadedRemoteFile download(String rawUrl, String requestedFilename) {
        URI currentUri = sanitizeAndValidate(rawUrl);

        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            HttpRequest request = HttpRequest.newBuilder(currentUri)
                    .timeout(readTimeout)
                    .header("User-Agent", "YuAgent-Remote-Importer/1.0")
                    .GET()
                    .build();

            try {
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int statusCode = response.statusCode();

                if (isRedirect(statusCode)) {
                    if (redirectCount == MAX_REDIRECTS) {
                        throw new BusinessException("远程文件重定向次数过多");
                    }
                    String location = response.headers().firstValue("location")
                            .orElseThrow(() -> new BusinessException("远程文件重定向失败"));
                    currentUri = sanitizeAndValidate(currentUri.resolve(location).toString());
                    continue;
                }

                if (statusCode >= 400) {
                    throw new BusinessException("远程文件下载失败，状态码: " + statusCode);
                }

                String contentType = resolveContentType(response.headers(), currentUri);
                long declaredLength = response.headers().firstValueAsLong("content-length").orElse(-1L);
                if (declaredLength > maxFileSize) {
                    throw new BusinessException("远程文件超过当前导入上限");
                }

                byte[] body = readWithinLimit(response.body());
                if (body.length == 0) {
                    throw new BusinessException("远程文件内容为空");
                }

                String detectedContentType = detectContentType(contentType, body);

                URI discoveredDownloadUri = discoverDownloadUri(currentUri, detectedContentType, body);
                if (discoveredDownloadUri != null) {
                    currentUri = discoveredDownloadUri;
                    continue;
                }

                ensureImportableBody(currentUri, detectedContentType, body);

                String filename = resolveFilename(requestedFilename, response.headers(), currentUri, detectedContentType);
                String extension = getExtension(filename);
                if (!SUPPORTED_EXTENSIONS.contains(extension)) {
                    throw new BusinessException("当前仅支持导入 PDF、Word、TXT、Markdown、HTML、CSV、Excel、JSON 文件");
                }

                return new DownloadedRemoteFile(rawUrl, filename, detectedContentType, body);
            } catch (HttpTimeoutException e) {
                throw new BusinessException("远程文件下载超时", e);
            } catch (IOException e) {
                throw new BusinessException("远程文件下载失败: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException("远程文件下载被中断");
            }
        }

        throw new BusinessException("远程文件下载失败");
    }

    private URI discoverDownloadUri(URI currentUri, String contentType, byte[] body) {
        URI ieeePdfUri = buildIeeePdfUri(currentUri);
        if (ieeePdfUri != null) {
            return ieeePdfUri;
        }

        if (!isHtmlLike(contentType)) {
            return null;
        }

        String html = new String(body, StandardCharsets.UTF_8);
        String extractedUrl = extractPdfLikeUrlFromHtml(html);
        if (!StringUtils.hasText(extractedUrl)) {
            return null;
        }
        return sanitizeAndValidate(currentUri.resolve(extractedUrl).toString());
    }

    private URI buildIeeePdfUri(URI currentUri) {
        if (!StringUtils.hasText(currentUri.getHost())) {
            return null;
        }
        if (!currentUri.getHost().toLowerCase(Locale.ROOT).contains("ieeexplore.ieee.org")) {
            return null;
        }
        String path = currentUri.getPath();
        if (!StringUtils.hasText(path) || !path.contains("/stamp/stamp.jsp")) {
            return null;
        }
        String query = currentUri.getRawQuery();
        if (!StringUtils.hasText(query) || !query.contains("arnumber=")) {
            return null;
        }
        return sanitizeAndValidate("https://ieeexplore.ieee.org/stampPDF/getPDF.jsp?" + query);
    }

    private boolean isHtmlLike(String contentType) {
        return StringUtils.hasText(contentType) && contentType.toLowerCase(Locale.ROOT).contains("text/html");
    }

    private String extractPdfLikeUrlFromHtml(String html) {
        if (!StringUtils.hasText(html)) {
            return null;
        }

        Matcher directPdfMatcher = DIRECT_PDF_PATTERN.matcher(html);
        if (directPdfMatcher.find()) {
            return directPdfMatcher.group();
        }

        List<String> candidates = new ArrayList<>();
        Matcher linkMatcher = HTML_DOWNLOAD_LINK_PATTERN.matcher(html);
        while (linkMatcher.find()) {
            String candidate = linkMatcher.group(1);
            if (StringUtils.hasText(candidate)) {
                candidates.add(candidate.trim());
            }
        }

        for (String candidate : candidates) {
            String lower = candidate.toLowerCase(Locale.ROOT);
            if (lower.contains(".pdf") || lower.contains("getpdf") || lower.contains("download")) {
                return candidate;
            }
        }
        return null;
    }

    private String detectContentType(String headerContentType, byte[] body) {
        if (isPdf(body)) {
            return "application/pdf";
        }
        if (looksLikeHtml(body)) {
            return "text/html";
        }
        return headerContentType;
    }

    private void ensureImportableBody(URI currentUri, String contentType, byte[] body) {
        if (!isHtmlLike(contentType)) {
            return;
        }

        String html = new String(body, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        if (isProtectedHtmlPage(html) || isProtectedAcademicLandingPage(currentUri, html)) {
            throw new BusinessException("璇ラ摼鎺ユ寚鍚戠殑鏄綉绔欓槻鎶撳彇鎴栨潈闄愭牎楠岄〉闈紝鏃犳硶鐩存帴瀵煎叆銆傝浣跨敤鍙洿鎺ヨ闂殑 PDF 閾炬帴锛屾垨鍏堝湪鏈湴涓嬭浇鍚庡啀涓婁紶");
        }
    }

    private boolean isProtectedHtmlPage(String html) {
        for (String marker : PROTECTED_HTML_MARKERS) {
            if (html.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private boolean isProtectedAcademicLandingPage(URI currentUri, String html) {
        if (!StringUtils.hasText(currentUri.getHost())) {
            return false;
        }
        String host = currentUri.getHost().toLowerCase(Locale.ROOT);
        return host.contains("ieeexplore.ieee.org")
                && !html.contains(".pdf")
                && (html.contains("ieee") || html.contains("xplore"));
    }

    private boolean isPdf(byte[] body) {
        return startsWith(body, "%PDF-".getBytes(StandardCharsets.US_ASCII));
    }

    private boolean looksLikeHtml(byte[] body) {
        if (body.length == 0) {
            return false;
        }
        String prefix = new String(body, 0, Math.min(body.length, 512), StandardCharsets.UTF_8)
                .toLowerCase(Locale.ROOT);
        return prefix.contains("<html") || prefix.contains("<!doctype html") || prefix.contains("<head")
                || prefix.contains("<body");
    }

    private boolean startsWith(byte[] body, byte[] prefix) {
        if (body.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (body[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private URI sanitizeAndValidate(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            throw new BusinessException("文件链接不能为空");
        }

        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (Exception e) {
            throw new BusinessException("文件链接格式不正确");
        }

        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new BusinessException("仅支持 HTTP / HTTPS 文件链接");
        }

        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new BusinessException("文件链接缺少域名");
        }

        validatePublicAddress(host);
        return uri;
    }

    private void validatePublicAddress(String host) {
        try {
            String asciiHost = IDN.toASCII(host);
            InetAddress[] addresses = InetAddress.getAllByName(asciiHost);
            for (InetAddress address : addresses) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress() || isUniqueLocalIpv6(address)) {
                    throw new BusinessException("不允许访问内网、本机或保留地址");
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("文件链接域名无法访问");
        }
    }

    private boolean isUniqueLocalIpv6(InetAddress address) {
        if (!(address instanceof Inet6Address inet6Address)) {
            return false;
        }
        byte firstByte = inet6Address.getAddress()[0];
        int unsignedFirstByte = firstByte & 0xFF;
        return unsignedFirstByte >= 0xFC && unsignedFirstByte <= 0xFD;
    }

    private boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308;
    }

    private String resolveContentType(HttpHeaders headers, URI uri) {
        String contentType = headers.firstValue("content-type")
                .map(value -> value.split(";")[0].trim())
                .filter(StringUtils::hasText)
                .orElse(null);
        if (StringUtils.hasText(contentType)) {
            return contentType;
        }
        String guessedFromName = URLConnection.guessContentTypeFromName(uri.getPath());
        return StringUtils.hasText(guessedFromName) ? guessedFromName : "application/octet-stream";
    }

    private byte[] readWithinLimit(InputStream inputStream) throws IOException {
        try (InputStream in = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long totalRead = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                totalRead += read;
                if (totalRead > maxFileSize) {
                    throw new BusinessException("远程文件超过当前导入上限");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private String resolveFilename(String requestedFilename, HttpHeaders headers, URI uri, String contentType) {
        if (StringUtils.hasText(requestedFilename)) {
            return normalizeFilename(requestedFilename, contentType);
        }

        Optional<String> disposition = headers.firstValue("content-disposition");
        if (disposition.isPresent()) {
            String parsed = parseFilenameFromDisposition(disposition.get());
            if (StringUtils.hasText(parsed)) {
                return normalizeFilename(parsed, contentType);
            }
        }

        String path = uri.getPath();
        if (StringUtils.hasText(path) && path.contains("/")) {
            String candidate = path.substring(path.lastIndexOf('/') + 1);
            if (StringUtils.hasText(candidate)) {
                return normalizeFilename(URLDecoder.decode(candidate, StandardCharsets.UTF_8), contentType);
            }
        }

        return normalizeFilename("remote-import", contentType);
    }

    private String parseFilenameFromDisposition(String contentDisposition) {
        for (String part : contentDisposition.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("filename*=")) {
                String value = trimmed.substring("filename*=".length());
                int quoteIndex = value.indexOf("''");
                if (quoteIndex >= 0 && quoteIndex + 2 < value.length()) {
                    return URLDecoder.decode(value.substring(quoteIndex + 2).replace("\"", ""),
                            StandardCharsets.UTF_8);
                }
            }
            if (trimmed.startsWith("filename=")) {
                return trimmed.substring("filename=".length()).replace("\"", "");
            }
        }
        return null;
    }

    private String normalizeFilename(String filename, String contentType) {
        String sanitized = filename.replace("\\", "_").replace("/", "_").trim();
        if (!StringUtils.hasText(sanitized)) {
            sanitized = "remote-import";
        }
        if (sanitized.length() > 255) {
            sanitized = sanitized.substring(0, 255);
        }
        String expectedExtension = extensionFromContentType(contentType);
        if (sanitized.contains(".")) {
            String currentExtension = getExtension(sanitized);
            if (StringUtils.hasText(expectedExtension) && !expectedExtension.equalsIgnoreCase(currentExtension)) {
                return sanitized.substring(0, sanitized.lastIndexOf('.') + 1) + expectedExtension;
            }
            return sanitized;
        }
        return StringUtils.hasText(expectedExtension) ? sanitized + "." + expectedExtension : sanitized;
    }

    private String extensionFromContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return null;
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "application/pdf" -> "pdf";
            case "application/msword" -> "doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
            case "text/plain" -> "txt";
            case "text/markdown" -> "md";
            case "text/html" -> "html";
            case "application/json" -> "json";
            case "text/csv" -> "csv";
            case "application/vnd.ms-excel" -> "xls";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx";
            default -> null;
        };
    }

    private String getExtension(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    public static final class DownloadedRemoteFile {
        private final String sourceUrl;
        private final String filename;
        private final String contentType;
        private final byte[] bytes;

        public DownloadedRemoteFile(String sourceUrl, String filename, String contentType, byte[] bytes) {
            this.sourceUrl = sourceUrl;
            this.filename = filename;
            this.contentType = contentType;
            this.bytes = bytes;
        }

        public String getSourceUrl() {
            return sourceUrl;
        }

        public String getFilename() {
            return filename;
        }

        public String getContentType() {
            return contentType;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public long getSize() {
            return bytes.length;
        }
    }
}
