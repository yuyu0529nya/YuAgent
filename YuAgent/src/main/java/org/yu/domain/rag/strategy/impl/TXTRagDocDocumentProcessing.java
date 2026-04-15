package org.yu.domain.rag.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

@Service("txt")
public class TXTRagDocDocumentProcessing extends AbstractDocumentProcessingStrategy {

    private static final Logger log = LoggerFactory.getLogger(TXTRagDocDocumentProcessing.class);

    private final DocumentUnitRepository documentUnitRepository;
    private final FileDetailRepository fileDetailRepository;
    private final StructuredPlainTextProcessor structuredPlainTextProcessor;

    @Resource
    private FileStorageService fileStorageService;

    private String currentProcessingFileId;

    public TXTRagDocDocumentProcessing(DocumentUnitRepository documentUnitRepository,
            FileDetailRepository fileDetailRepository, StructuredPlainTextProcessor structuredPlainTextProcessor) {
        this.documentUnitRepository = documentUnitRepository;
        this.fileDetailRepository = fileDetailRepository;
        this.structuredPlainTextProcessor = structuredPlainTextProcessor;
    }

    @Override
    public void handle(RagDocMessage ragDocMessage, String strategy) throws Exception {
        this.currentProcessingFileId = ragDocMessage.getFileId();
        super.handle(ragDocMessage, strategy);
    }

    @Override
    public void pushPageSize(byte[] bytes, RagDocMessage ragDocSyncOcrMessage) {
        try {
            int segmentCount = buildSegments(bytes).size();
            ragDocSyncOcrMessage.setPageSize(segmentCount);
            if (currentProcessingFileId != null) {
                LambdaUpdateWrapper<FileDetailEntity> wrapper = Wrappers.<FileDetailEntity>lambdaUpdate()
                        .eq(FileDetailEntity::getId, currentProcessingFileId)
                        .set(FileDetailEntity::getFilePageSize, segmentCount);
                fileDetailRepository.update(wrapper);
            }
            log.info("TXT document chunked into {} structured segment(s)", segmentCount);
        } catch (Exception e) {
            log.error("Failed to calculate TXT segment count", e);
            ragDocSyncOcrMessage.setPageSize(0);
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
        log.info("Using structure-aware chunking for TXT document ingestion");
        Map<Integer, String> segmentsByIndex = new LinkedHashMap<>();
        List<ProcessedSegment> segments = buildSegments(fileBytes);
        for (int i = 0; i < segments.size(); i++) {
            segmentsByIndex.put(i, segments.get(i).getContent());
        }
        return segmentsByIndex;
    }

    @Override
    public void insertData(RagDocMessage ragDocSyncOcrMessage, Map<Integer, String> ocrData) {
        log.info("Persisting {} TXT segment(s)", ocrData.size());

        for (int pageIndex = 0; pageIndex < ocrData.size(); pageIndex++) {
            String content = ocrData.get(pageIndex);

            DocumentUnitEntity entity = new DocumentUnitEntity();
            entity.setContent(content);
            entity.setPage(pageIndex);
            entity.setFileId(ragDocSyncOcrMessage.getFileId());
            entity.setIsVector(false);
            entity.setIsOcr(StringUtils.hasText(content));

            ProcessedSegment segment = buildPersistedSegment(content, pageIndex);
            DocumentUnitMetadataSupport.apply(entity, segment, pageIndex);

            documentUnitRepository.checkInsert(entity);
        }
    }

    private List<ProcessedSegment> buildSegments(byte[] fileBytes) {
        String text = new String(fileBytes, StandardCharsets.UTF_8);
        return structuredPlainTextProcessor.process(text);
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
}
