package org.yu.infrastructure.utils;

public class ModelResponseToJsonUtils {

    public static <T> T toJson(String text, Class<T> classz) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String normalized = text.trim();
        if (normalized.startsWith("```")) {
            int firstLineBreak = normalized.indexOf('\n');
            int closingFence = normalized.lastIndexOf("```");
            if (firstLineBreak >= 0 && closingFence > firstLineBreak) {
                normalized = normalized.substring(firstLineBreak + 1, closingFence).trim();
            }
        }

        int jsonStart = normalized.indexOf('{');
        int jsonEnd = normalized.lastIndexOf('}');
        if (jsonStart < 0 || jsonEnd < jsonStart) {
            return null;
        }

        String json = normalized.substring(jsonStart, jsonEnd + 1);
        return JsonUtils.parseObject(json, classz);
    }
}
