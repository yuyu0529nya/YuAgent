package org.yu.infrastructure.rag.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;
import org.yu.domain.rag.constant.MetadataConstant;
import org.yu.domain.rag.model.DocumentUnitEntity;
import org.yu.domain.rag.model.ProcessedSegment;

/** Helpers for persisting chunk metadata into {@code document_unit}. */
public final class DocumentUnitMetadataSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DocumentUnitMetadataSupport() {
    }

    public static void apply(DocumentUnitEntity entity, ProcessedSegment segment, Integer fallbackSourcePage) {
        if (entity == null) {
            return;
        }

        entity.setSourcePage(fallbackSourcePage);
        if (segment == null) {
            entity.setSegmentType(null);
            entity.setSegmentOrder(null);
            entity.setTitlePath(inferTitlePath(entity.getContent()));
            entity.setMetadataJson(null);
            return;
        }

        Map<String, Object> metadata = segment.getMetadata();
        String titlePath = readString(metadata, MetadataConstant.TITLE_PATH);
        if (!StringUtils.hasText(titlePath)) {
            titlePath = inferTitlePath(segment.getContent());
        }

        entity.setTitlePath(titlePath);
        entity.setSegmentType(segment.getType() != null ? segment.getType().getValue()
                : readString(metadata, MetadataConstant.SEGMENT_TYPE));
        entity.setSegmentOrder(readInteger(metadata, MetadataConstant.SEGMENT_ORDER, segment.getOrder()));

        Integer sourcePage = readInteger(metadata, MetadataConstant.SOURCE_PAGE, fallbackSourcePage);
        entity.setSourcePage(sourcePage);
        entity.setMetadataJson(toJson(metadata));
    }

    public static String inferTitlePath(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }

        String normalized = content.replace("\r\n", "\n").trim();
        int separator = normalized.indexOf("\n\n");
        if (separator > 0) {
            String firstBlock = normalized.substring(0, separator).trim();
            if (firstBlock.contains(" > ") && firstBlock.length() <= 300) {
                return firstBlock;
            }
        }

        String[] lines = content.split("\\r?\\n");
        List<String> titles = new ArrayList<>();
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                if (!titles.isEmpty()) {
                    break;
                }
                continue;
            }
            if (line.startsWith("#")) {
                titles.add(line.replaceFirst("^#+\\s*", "").trim());
                continue;
            }
            break;
        }

        if (titles.isEmpty()) {
            return null;
        }
        return String.join(" > ", titles);
    }

    private static String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String readString(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null ? null : value.toString();
    }

    private static Integer readInteger(Map<String, Object> metadata, String key, Integer fallback) {
        if (metadata == null) {
            return fallback;
        }
        Object value = metadata.get(key);
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
