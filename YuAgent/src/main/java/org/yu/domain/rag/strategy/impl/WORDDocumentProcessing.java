package org.yu.domain.rag.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.dromara.x.file.storage.core.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yu.domain.rag.constant.MetadataConstant;
import org.yu.domain.rag.message.RagDocMessage;
import org.yu.domain.rag.model.DocumentUnitEntity;
import org.yu.domain.rag.model.FileDetailEntity;
import org.yu.domain.rag.model.ProcessedSegment;
import org.yu.domain.rag.model.enums.SegmentType;
import org.yu.domain.rag.repository.DocumentUnitRepository;
import org.yu.domain.rag.repository.FileDetailRepository;
import org.yu.infrastructure.rag.processor.DocumentUnitMetadataSupport;
import org.yu.infrastructure.rag.processor.StructuredPlainTextProcessor;
import org.yu.infrastructure.utils.JsonUtils;

@Service("word")
public class WORDDocumentProcessing extends AbstractDocumentProcessingStrategy {

    private static final Logger log = LoggerFactory.getLogger(WORDDocumentProcessing.class);
    private static final String ATTR_PROCESSING_STAGE = "processingStage";
    private static final String ATTR_PROCESSING_STAGE_DESCRIPTION = "processingStageDescription";
    private static final String STAGE_READING = "WORD_READING";
    private static final String STAGE_SEGMENTING = "WORD_SEGMENTING";
    private static final String STAGE_PERSISTING = "WORD_PERSISTING";

    private final DocumentUnitRepository documentUnitRepository;
    private final FileDetailRepository fileDetailRepository;
    private final StructuredPlainTextProcessor structuredPlainTextProcessor;

    @Resource
    private FileStorageService fileStorageService;

    private final ThreadLocal<String> currentProcessingFileId = new ThreadLocal<>();
    private final ThreadLocal<Map<String, Object>> currentAttrCache = ThreadLocal.withInitial(HashMap::new);
    private final ThreadLocal<List<ProcessedSegment>> cachedSegments = new ThreadLocal<>();

    public WORDDocumentProcessing(DocumentUnitRepository documentUnitRepository,
            FileDetailRepository fileDetailRepository, StructuredPlainTextProcessor structuredPlainTextProcessor) {
        this.documentUnitRepository = documentUnitRepository;
        this.fileDetailRepository = fileDetailRepository;
        this.structuredPlainTextProcessor = structuredPlainTextProcessor;
    }

    @Override
    public void handle(RagDocMessage ragDocMessage, String strategy) throws Exception {
        currentProcessingFileId.set(ragDocMessage.getFileId());
        currentAttrCache.remove();
        cachedSegments.remove();
        try {
            super.handle(ragDocMessage, strategy);
        } finally {
            currentProcessingFileId.remove();
            currentAttrCache.remove();
            cachedSegments.remove();
        }
    }

    @Override
    public void pushPageSize(byte[] bytes, RagDocMessage ragDocMessage) {
        try {
            List<ProcessedSegment> segments = getOrBuildSegments(bytes);
            int segmentCount = segments.size();
            ragDocMessage.setPageSize(segmentCount);

            String fileId = getCurrentProcessingFileId();
            if (fileId != null) {
                LambdaUpdateWrapper<FileDetailEntity> wrapper = Wrappers.<FileDetailEntity>lambdaUpdate()
                        .eq(FileDetailEntity::getId, fileId).set(FileDetailEntity::getFilePageSize, segmentCount);
                fileDetailRepository.update(wrapper);
            }
            log.info("Word document chunked into {} structured segment(s)", segmentCount);
        } catch (Exception e) {
            log.error("Failed to calculate Word segment count", e);
            ragDocMessage.setPageSize(0);
        }
    }

    @Override
    public byte[] getFileData(RagDocMessage ragDocSyncOcrMessage, String strategy) {
        FileDetailEntity fileDetailEntity = fileDetailRepository.selectById(ragDocSyncOcrMessage.getFileId());
        if (fileDetailEntity == null) {
            log.error("File not found: {}", ragDocSyncOcrMessage.getFileId());
            return new byte[0];
        }
        return fileStorageService.download(fileDetailEntity.getUrl()).bytes();
    }

    @Override
    public Map<Integer, String> processFile(byte[] fileBytes, int totalPages) {
        log.info("Using structure-aware chunking for Word document ingestion");
        List<ProcessedSegment> segments = getOrBuildSegments(fileBytes);
        Map<Integer, String> segmentsByIndex = new LinkedHashMap<>();
        for (int i = 0; i < segments.size(); i++) {
            segmentsByIndex.put(i, segments.get(i).getContent());
        }
        return segmentsByIndex;
    }

    @Override
    public void insertData(RagDocMessage ragDocSyncOcrMessage, Map<Integer, String> ocrData) {
        updateProcessingStage(STAGE_PERSISTING, "Word 解析完成，正在写入分段结果");
        List<ProcessedSegment> segments = cachedSegments.get();
        log.info("Persisting {} Word segment(s)", ocrData.size());

        for (int pageIndex = 0; pageIndex < ocrData.size(); pageIndex++) {
            String content = ocrData.get(pageIndex);

            DocumentUnitEntity entity = new DocumentUnitEntity();
            entity.setContent(content);
            entity.setPage(pageIndex);
            entity.setFileId(ragDocSyncOcrMessage.getFileId());
            entity.setIsVector(false);
            entity.setIsOcr(StringUtils.hasText(content));

            ProcessedSegment segment = pageIndex < (segments != null ? segments.size() : 0)
                    ? ensurePersistedSegment(segments.get(pageIndex), content, pageIndex)
                    : buildPersistedSegment(content, pageIndex);
            DocumentUnitMetadataSupport.apply(entity, segment, pageIndex);

            documentUnitRepository.checkInsert(entity);
        }
    }

    private List<ProcessedSegment> getOrBuildSegments(byte[] fileBytes) {
        List<ProcessedSegment> existing = cachedSegments.get();
        if (existing != null) {
            return existing;
        }
        updateProcessingStage(STAGE_READING, "正在读取 Word 文档内容");
        String text = extractText(fileBytes, resolveCurrentFileExtension());
        updateProcessingStage(STAGE_SEGMENTING, "Word 文档读取完成，正在结构化切块");
        List<ProcessedSegment> segments = structuredPlainTextProcessor.process(text);
        cachedSegments.set(segments);
        return segments;
    }

    private String extractText(byte[] fileBytes, String fileExtension) {
        String normalizedExtension = fileExtension != null ? fileExtension.trim().toLowerCase() : "";
        try {
            if ("doc".equals(normalizedExtension)) {
                return extractDocText(new ByteArrayInputStream(fileBytes));
            }
            return extractDocxText(new ByteArrayInputStream(fileBytes));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read Word document", e);
        }
    }

    private String extractDocxText(InputStream inputStream) throws IOException {
        byte[] bytes = inputStream.readAllBytes();
        String fastText = extractDocxTextFromXml(bytes);
        if (StringUtils.hasText(fastText)) {
            return fastText;
        }
        try (ByteArrayInputStream poiInput = new ByteArrayInputStream(bytes);
                XWPFDocument document = new XWPFDocument(poiInput);
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractDocxTextFromXml(byte[] fileBytes) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(fileBytes),
                StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!"word/document.xml".equals(entry.getName())) {
                    continue;
                }
                String xml = new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
                String normalized = xml.replaceAll("</w:p>", "\n").replaceAll("<w:tab[^>]*/>", "\t")
                        .replaceAll("<w:br[^>]*/>", "\n").replaceAll("</w:tr>", "\n");
                String text = normalized.replaceAll("<[^>]+>", " ").replace("&lt;", "<").replace("&gt;", ">")
                        .replace("&amp;", "&").replace("&quot;", "\"").replace("&apos;", "'");
                return text.replaceAll("[\\t\\x0B\\f\\r ]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
            }
        }
        return null;
    }

    private String extractDocText(InputStream inputStream) throws IOException {
        try (HWPFDocument document = new HWPFDocument(inputStream);
                WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String resolveCurrentFileExtension() {
        String fileId = getCurrentProcessingFileId();
        if (!StringUtils.hasText(fileId)) {
            return "docx";
        }
        FileDetailEntity fileDetailEntity = fileDetailRepository.selectById(fileId);
        if (fileDetailEntity == null || !StringUtils.hasText(fileDetailEntity.getExt())) {
            return "docx";
        }
        return fileDetailEntity.getExt();
    }

    private ProcessedSegment buildPersistedSegment(String content, int order) {
        String titlePath = DocumentUnitMetadataSupport.inferTitlePath(content);
        SegmentType type = StringUtils.hasText(titlePath) ? SegmentType.SECTION : SegmentType.TEXT;
        ProcessedSegment segment = new ProcessedSegment(content, type, null);
        segment.setOrder(order);
        segment.addMetadata(MetadataConstant.SEGMENT_ORDER, order);
        if (StringUtils.hasText(titlePath)) {
            segment.addMetadata(MetadataConstant.TITLE_PATH, titlePath);
        }
        return segment;
    }

    private ProcessedSegment ensurePersistedSegment(ProcessedSegment original, String fallbackContent, int order) {
        if (original == null) {
            return buildPersistedSegment(fallbackContent, order);
        }
        if (!StringUtils.hasText(original.getContent()) && StringUtils.hasText(fallbackContent)) {
            original.setContent(fallbackContent);
        }
        if (original.getOrder() < 0) {
            original.setOrder(order);
        }
        original.addMetadata(MetadataConstant.SEGMENT_ORDER, order);
        if (original.getType() == null) {
            original.setType(SegmentType.TEXT);
        }
        return original;
    }

    private void updateProcessingStage(String stage, String description) {
        String fileId = getCurrentProcessingFileId();
        if (!StringUtils.hasText(fileId)) {
            return;
        }

        try {
            Map<String, Object> attrMap = currentAttrCache.get();
            if (attrMap.isEmpty()) {
                FileDetailEntity fileDetailEntity = fileDetailRepository.selectById(fileId);
                Map<String, Object> persistedAttrMap = JsonUtils
                        .parseMap(fileDetailEntity != null ? fileDetailEntity.getAttr() : null);
                if (persistedAttrMap != null) {
                    attrMap.putAll(persistedAttrMap);
                }
            }
            attrMap.put(ATTR_PROCESSING_STAGE, stage);
            attrMap.put(ATTR_PROCESSING_STAGE_DESCRIPTION, description);

            LambdaUpdateWrapper<FileDetailEntity> wrapper = Wrappers.<FileDetailEntity>lambdaUpdate()
                    .eq(FileDetailEntity::getId, fileId)
                    .set(FileDetailEntity::getAttr, JsonUtils.toJsonString(attrMap));
            fileDetailRepository.update(wrapper);
        } catch (Exception e) {
            log.warn("Failed to update processing stage for Word file {}: {}", fileId, e.getMessage());
        }
    }

    private String getCurrentProcessingFileId() {
        return currentProcessingFileId.get();
    }
}
