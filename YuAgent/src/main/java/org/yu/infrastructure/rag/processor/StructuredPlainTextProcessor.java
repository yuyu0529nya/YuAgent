package org.yu.infrastructure.rag.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yu.domain.rag.constant.MetadataConstant;
import org.yu.domain.rag.model.ProcessedSegment;
import org.yu.domain.rag.model.enums.SegmentType;

/** Structure-aware chunker for plain text style documents such as TXT and Word exports. */
@Component
public class StructuredPlainTextProcessor {

    private static final Logger log = LoggerFactory.getLogger(StructuredPlainTextProcessor.class);

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern DECIMAL_HEADING = Pattern
            .compile("^(\\d+(?:\\.\\d+){0,4}|\\d+[\\)\\.]|[A-Z][\\)\\.])\\s+(.+)$");
    private static final Pattern CN_HEADING = Pattern
            .compile("^(第[一二三四五六七八九十百千万0-9]+[章节部分篇]|[一二三四五六七八九十]+[、.)])\\s*(.+)$");

    private final MarkdownContentSplitter contentSplitter;

    public StructuredPlainTextProcessor(MarkdownContentSplitter contentSplitter) {
        this.contentSplitter = contentSplitter;
    }

    public List<ProcessedSegment> process(String text) {
        if (text == null || text.trim().isEmpty()) {
            return List.of();
        }

        String normalized = normalizeText(text);
        List<String> paragraphs = splitParagraphs(normalized);
        if (paragraphs.isEmpty()) {
            return List.of();
        }

        List<SectionAccumulator> sections = buildSections(paragraphs);
        List<ProcessedSegment> segments = new ArrayList<>();

        for (SectionAccumulator section : sections) {
            String body = section.content().trim();
            if (body.isEmpty()) {
                continue;
            }

            List<String> chunks = contentSplitter.splitIfNeeded(body, section.titlePath());
            for (String chunk : chunks) {
                String normalizedChunk = chunk == null ? null : chunk.trim();
                if (normalizedChunk == null || normalizedChunk.isEmpty()) {
                    continue;
                }
                ProcessedSegment segment = new ProcessedSegment(normalizedChunk, SegmentType.SECTION, null);
                if (section.titlePath() != null && !section.titlePath().isBlank()) {
                    segment.addMetadata(MetadataConstant.TITLE_PATH, section.titlePath());
                }
                segments.add(segment);
            }
        }

        if (segments.isEmpty()) {
            for (String chunk : contentSplitter.splitIfNeeded(normalized, null)) {
                if (chunk == null || chunk.trim().isEmpty()) {
                    continue;
                }
                segments.add(new ProcessedSegment(chunk.trim(), SegmentType.TEXT, null));
            }
        }

        for (int i = 0; i < segments.size(); i++) {
            ProcessedSegment segment = segments.get(i);
            segment.setOrder(i);
            segment.addMetadata(MetadataConstant.SEGMENT_ORDER, i);
            segment.addMetadata(MetadataConstant.SEGMENT_TYPE,
                    segment.getType() != null ? segment.getType().getValue() : SegmentType.TEXT.getValue());
        }

        log.info("Structured plain text processing produced {} segment(s)", segments.size());
        return segments;
    }

    private List<SectionAccumulator> buildSections(List<String> paragraphs) {
        List<SectionAccumulator> sections = new ArrayList<>();
        List<HeadingEntry> headingStack = new ArrayList<>();
        StringBuilder currentBody = new StringBuilder();
        String currentTitlePath = null;

        for (String paragraph : paragraphs) {
            HeadingMatch heading = detectHeading(paragraph);
            if (heading != null) {
                flushSection(sections, currentTitlePath, currentBody);
                currentBody.setLength(0);

                while (!headingStack.isEmpty()
                        && headingStack.get(headingStack.size() - 1).level() >= heading.level()) {
                    headingStack.remove(headingStack.size() - 1);
                }
                headingStack.add(new HeadingEntry(heading.level(), heading.title()));
                currentTitlePath = buildTitlePath(headingStack);
                continue;
            }

            if (currentBody.length() > 0) {
                currentBody.append("\n\n");
            }
            currentBody.append(paragraph);
        }

        flushSection(sections, currentTitlePath, currentBody);
        return sections;
    }

    private void flushSection(List<SectionAccumulator> sections, String titlePath, StringBuilder body) {
        if (body.length() == 0) {
            return;
        }
        sections.add(new SectionAccumulator(titlePath, body.toString()));
    }

    private String normalizeText(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n').replace('\u00A0', ' ').trim();
    }

    private List<String> splitParagraphs(String text) {
        String[] blocks = text.split("\\n\\s*\\n+");
        List<String> paragraphs = new ArrayList<>();
        for (String block : blocks) {
            String normalized = block.trim();
            if (!normalized.isEmpty()) {
                paragraphs.add(normalized);
            }
        }
        return paragraphs;
    }

    private HeadingMatch detectHeading(String paragraph) {
        String line = paragraph.strip();
        if (line.length() > 120 || line.contains("\n")) {
            return null;
        }

        Matcher markdown = MARKDOWN_HEADING.matcher(line);
        if (markdown.matches()) {
            return new HeadingMatch(markdown.group(1).length(), markdown.group(2).trim());
        }

        Matcher decimal = DECIMAL_HEADING.matcher(line);
        if (decimal.matches()) {
            return new HeadingMatch(inferDecimalLevel(decimal.group(1)), decimal.group(2).trim());
        }

        Matcher chinese = CN_HEADING.matcher(line);
        if (chinese.matches()) {
            return new HeadingMatch(inferChineseLevel(chinese.group(1)), chinese.group(2).trim());
        }

        if (looksLikeShortStandaloneTitle(line)) {
            return new HeadingMatch(1, line);
        }

        return null;
    }

    private boolean looksLikeShortStandaloneTitle(String line) {
        if (line.length() < 4 || line.length() > 30) {
            return false;
        }
        if (line.endsWith("。") || line.endsWith(".") || line.endsWith("？") || line.endsWith("?") || line.endsWith("；")
                || line.endsWith(";")) {
            return false;
        }
        long punctuationCount = line.chars().filter(ch -> ch == ':' || ch == '：').count();
        if (punctuationCount > 0) {
            return false;
        }
        return line.chars().filter(Character::isWhitespace).count() <= 2;
    }

    private int inferDecimalLevel(String prefix) {
        long dots = prefix.chars().filter(ch -> ch == '.').count();
        return (int) Math.min(6, dots + 1);
    }

    private int inferChineseLevel(String prefix) {
        if (prefix.startsWith("第")) {
            return prefix.contains("章") || prefix.contains("篇") ? 1 : 2;
        }
        return 2;
    }

    private String buildTitlePath(List<HeadingEntry> headingStack) {
        return headingStack.stream().map(HeadingEntry::title).filter(Objects::nonNull).map(String::trim)
                .filter(title -> !title.isEmpty()).reduce((left, right) -> left + " > " + right).orElse(null);
    }

    private record HeadingMatch(int level, String title) {
    }

    private record HeadingEntry(int level, String title) {
    }

    private record SectionAccumulator(String titlePath, String content) {
    }
}
