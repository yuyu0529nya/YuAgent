package org.yu.infrastructure.mcp_gateway;

import org.springframework.util.StringUtils;
import org.yu.infrastructure.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class HostedMcpInstallCommandHelper {

    private static final Set<String> SERVER_CONFIG_KEYS = Set.of("type", "url", "baseUrl", "headers", "command",
            "args", "env", "name", "isActive", "description", "workspace");

    private HostedMcpInstallCommandHelper() {
    }

    public record NormalizedInstallCommand(String serverName, Map<String, Object> installCommand, boolean hostedConfig,
            String transportType) {
    }

    public static NormalizedInstallCommand normalizeInstallCommand(Map<String, Object> installCommand,
            String preferredServerName, String preferredDisplayName) {
        if (installCommand == null || installCommand.isEmpty()) {
            throw new BusinessException("安装命令不能为空");
        }

        Object mcpServersValue = installCommand.get("mcpServers");
        if (mcpServersValue instanceof Map<?, ?> mcpServers && !mcpServers.isEmpty()) {
            if (looksLikeServerConfigMap(mcpServers)) {
                Map<String, Object> normalizedServerConfig = normalizeServerConfig(castStringObjectMap(mcpServers));
                String serverName = resolveServerName(normalizedServerConfig, preferredServerName, preferredDisplayName);
                return buildNormalizedCommand(serverName, normalizedServerConfig);
            }

            Map.Entry<?, ?> firstServerEntry = mcpServers.entrySet().iterator().next();
            if (!(firstServerEntry.getValue() instanceof Map<?, ?> firstServerConfig)) {
                throw new BusinessException("安装命令中的 mcpServers 配置格式不正确");
            }

            Map<String, Object> normalizedServerConfig = normalizeServerConfig(castStringObjectMap(firstServerConfig));
            String serverName = resolveServerName(normalizedServerConfig, String.valueOf(firstServerEntry.getKey()),
                    preferredDisplayName);
            return buildNormalizedCommand(serverName, normalizedServerConfig);
        }

        if (looksLikeServerConfigMap(installCommand)) {
            Map<String, Object> normalizedServerConfig = normalizeServerConfig(installCommand);
            String serverName = resolveServerName(normalizedServerConfig, preferredServerName, preferredDisplayName);
            return buildNormalizedCommand(serverName, normalizedServerConfig);
        }

        throw new BusinessException("安装命令缺少有效的 mcpServers 配置");
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getFirstServerConfig(Map<String, Object> installCommand) {
        try {
            NormalizedInstallCommand normalizedInstallCommand = normalizeInstallCommand(installCommand, null, null);
            Object mcpServersValue = normalizedInstallCommand.installCommand().get("mcpServers");
            if (!(mcpServersValue instanceof Map<?, ?> mcpServers) || mcpServers.isEmpty()) {
                return null;
            }
            Object firstServerValue = mcpServers.values().iterator().next();
            if (!(firstServerValue instanceof Map<?, ?> firstServerConfig)) {
                return null;
            }
            return castStringObjectMap(firstServerConfig);
        } catch (BusinessException ex) {
            return null;
        }
    }

    public static boolean isHostedConfig(Map<String, Object> installCommand) {
        try {
            return normalizeInstallCommand(installCommand, null, null).hostedConfig();
        } catch (BusinessException ex) {
            return false;
        }
    }

    public static String getHostedTransportType(Map<String, Object> installCommand) {
        try {
            return normalizeInstallCommand(installCommand, null, null).transportType();
        } catch (BusinessException ex) {
            return null;
        }
    }

    private static NormalizedInstallCommand buildNormalizedCommand(String serverName, Map<String, Object> serverConfig) {
        Map<String, Object> mcpServers = new LinkedHashMap<>();
        mcpServers.put(serverName, serverConfig);

        Map<String, Object> normalizedInstallCommand = new LinkedHashMap<>();
        normalizedInstallCommand.put("mcpServers", mcpServers);

        boolean hostedConfig = isHostedServerConfig(serverConfig);
        return new NormalizedInstallCommand(serverName, normalizedInstallCommand, hostedConfig,
                resolveTransportType(serverConfig));
    }

    private static boolean looksLikeServerConfigMap(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return false;
        }
        return map.keySet().stream().allMatch(key -> key instanceof String stringKey && SERVER_CONFIG_KEYS.contains(stringKey));
    }

    private static Map<String, Object> normalizeServerConfig(Map<String, Object> rawServerConfig) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawServerConfig.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nestedMap) {
                normalized.put(key, castStringObjectMap(nestedMap));
            } else if (value instanceof List<?> listValue) {
                normalized.put(key, List.copyOf(listValue));
            } else {
                normalized.put(key, value);
            }
        }

        if (!StringUtils.hasText(stringValue(normalized.get("type")))) {
            String inferredType = inferTransportTypeFromUrl(normalized);
            if (StringUtils.hasText(inferredType)) {
                normalized.put("type", inferredType);
            }
        }

        return normalized;
    }

    private static String resolveServerName(Map<String, Object> serverConfig, String preferredServerName,
            String preferredDisplayName) {
        String candidate = firstNonBlank(stringValue(serverConfig.get("name")), preferredServerName, preferredDisplayName,
                "mcp-server");

        String normalized = candidate.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("(^-+|-+$)", "").replaceAll("-{2,}", "-");
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }

        String seed = firstNonBlank(candidate, stringValue(serverConfig.get("url")), stringValue(serverConfig.get("baseUrl")),
                UUID.randomUUID().toString());
        return "mcp-" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString().substring(0, 8);
    }

    private static boolean isHostedServerConfig(Map<String, Object> serverConfig) {
        return !StringUtils.hasText(stringValue(serverConfig.get("command")))
                && (StringUtils.hasText(stringValue(serverConfig.get("url")))
                        || StringUtils.hasText(stringValue(serverConfig.get("baseUrl")))
                        || serverConfig.containsKey("headers") || serverConfig.containsKey("type"));
    }

    private static String resolveTransportType(Map<String, Object> serverConfig) {
        if (StringUtils.hasText(stringValue(serverConfig.get("command")))) {
            return "stdio";
        }

        String configuredType = stringValue(serverConfig.get("type"));
        if (StringUtils.hasText(configuredType)) {
            return configuredType.trim();
        }

        return inferTransportTypeFromUrl(serverConfig);
    }

    private static String inferTransportTypeFromUrl(Map<String, Object> serverConfig) {
        String endpoint = firstNonBlank(stringValue(serverConfig.get("baseUrl")), stringValue(serverConfig.get("url")));
        if (!StringUtils.hasText(endpoint)) {
            return null;
        }
        String lowerEndpoint = endpoint.toLowerCase(Locale.ROOT);
        if (lowerEndpoint.contains("/sse")) {
            return "sse";
        }
        if (lowerEndpoint.contains("/mcp")) {
            return "streamableHttp";
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castStringObjectMap(Map<?, ?> map) {
        Map<String, Object> casted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            casted.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return casted;
    }
}
