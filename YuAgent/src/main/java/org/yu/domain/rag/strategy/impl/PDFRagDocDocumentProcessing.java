package org.yu.domain.rag.strategy.impl;

import static org.yu.domain.rag.strategy.context.RAGSystemPrompt.OCR_PROMPT;

import cn.hutool.core.codec.Base64;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.dromara.x.file.storage.core.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.yu.domain.rag.message.RagDocMessage;
import org.yu.domain.rag.model.DocumentUnitEntity;
import org.yu.domain.rag.model.FileDetailEntity;
import org.yu.domain.rag.model.ProcessedSegment;
import org.yu.domain.rag.model.enums.SegmentType;
import org.yu.domain.rag.repository.DocumentUnitRepository;
import org.yu.domain.rag.repository.FileDetailRepository;
import org.yu.infrastructure.exception.BusinessException;
import org.yu.infrastructure.llm.LLMProviderService;
import org.yu.infrastructure.llm.config.ProviderConfig;
import org.yu.infrastructure.llm.protocol.enums.ProviderProtocol;
import org.yu.infrastructure.rag.detector.TikaFileTypeDetector;
import org.yu.infrastructure.rag.processor.DocumentUnitMetadataSupport;
import org.yu.infrastructure.rag.processor.StructuredPlainTextProcessor;
import org.yu.infrastructure.rag.utils.PdfToBase64Converter;
import org.yu.infrastructure.utils.JsonUtils;

@Service("pdf")
public class PDFRagDocDocumentProcessing extends AbstractDocumentProcessingStrategy {

    private static final Logger log = LoggerFactory.getLogger(PDFRagDocDocumentProcessing.class);
    private static final long OCR_PAGE_TIMEOUT_SECONDS = 90;
    private static final int MIN_TEXT_LENGTH_FOR_NATIVE_PDF = 12;
    private static final String ATTR_PROCESSING_STAGE = "processingStage";
    private static final String ATTR_PROCESSING_STAGE_DESCRIPTION = "processingStageDescription";
    private static final String STAGE_NATIVE_EXTRACTING = "PDF_NATIVE_EXTRACTING";
    private static final String STAGE_OCR_FALLBACK = "PDF_OCR_FALLBACK";
    private static final String STAGE_SEGMENTING = "PDF_SEGMENTING";

    private static final Pattern[] PATTERNS = {Pattern.compile("\\\\\\("), Pattern.compile("\\\\\\)"),
            Pattern.compile("\n{3,}"), Pattern.compile("([^\n])\n([^\n])"), Pattern.compile("\\$\\s+"),
            Pattern.compile("\\s+\\$"), Pattern.compile("\\$\\$")};

    private final DocumentUnitRepository documentUnitRepository;
    private final FileDetailRepository fileDetailRepository;
    private final StructuredPlainTextProcessor structuredPlainTextProcessor;
    private final ThreadPoolTaskExecutor ocrTaskExecutor;

    @Resource
    private FileStorageService fileStorageService;

    @Value("${rag.pdf.ocr-render-dpi:132}")
    private float ocrRenderDpi;

    @Value("${rag.pdf.ocr-render-dpi-large:120}")
    private float ocrRenderDpiLarge;

    private final ThreadLocal<String> currentProcessingFileId = new ThreadLocal<>();
    private final ThreadLocal<Map<String, Object>> currentAttrCache = ThreadLocal.withInitial(HashMap::new);

    public PDFRagDocDocumentProcessing(DocumentUnitRepository documentUnitRepository,
            FileDetailRepository fileDetailRepository, StructuredPlainTextProcessor structuredPlainTextProcessor,
            @Qualifier("ocrTaskExecutor") ThreadPoolTaskExecutor ocrTaskExecutor) {
        this.documentUnitRepository = documentUnitRepository;
        this.fileDetailRepository = fileDetailRepository;
        this.structuredPlainTextProcessor = structuredPlainTextProcessor;
        this.ocrTaskExecutor = ocrTaskExecutor;
    }

    @Override
    public void handle(RagDocMessage ragDocMessage, String strategy) throws Exception {
        currentProcessingFileId.set(ragDocMessage.getFileId());
        currentAttrCache.remove();
        try {
            super.handle(ragDocMessage, strategy);
        } finally {
            currentProcessingFileId.remove();
            currentAttrCache.remove();
        }
    }

    @Override
    public void pushPageSize(byte[] bytes, RagDocMessage ragDocSyncOcrMessage) {
        try {
            int pdfPageCount = PdfToBase64Converter.getPdfPageCount(bytes);
            ragDocSyncOcrMessage.setPageSize(pdfPageCount);

            String fileId = getCurrentProcessingFileId();
            if (fileId != null) {
                LambdaUpdateWrapper<FileDetailEntity> wrapper = Wrappers.<FileDetailEntity>lambdaUpdate()
                        .eq(FileDetailEntity::getId, fileId).set(FileDetailEntity::getFilePageSize, pdfPageCount);
                fileDetailRepository.update(wrapper);
                log.info("Updated total page count for file {}: {}", fileId, pdfPageCount);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] getFileData(RagDocMessage ragDocSyncOcrMessage, String strategy) {
        FileDetailEntity fileDetailEntity = fileDetailRepository.selectById(ragDocSyncOcrMessage.getFileId());
        return fileStorageService.download(fileDetailEntity.getUrl()).bytes();
    }

    @Override
    public Map<Integer, String> processFile(byte[] fileBytes, int totalPages) {
        return processFile(fileBytes, totalPages, null);
    }

    @Override
    public Map<Integer, String> processFile(byte[] fileBytes, int totalPages, RagDocMessage ragDocSyncOcrMessage) {
        Map<Integer, String> pageContent = new HashMap<>();
        Map<Integer, String> nativeTextPages = extractTextByPage(fileBytes, totalPages);
        ChatModel ocrModel = null;
        int successPages = 0;
        int nativePageCount = 0;
        float renderDpi = resolveRenderDpi(fileBytes, totalPages);

        updateProcessingStage(STAGE_NATIVE_EXTRACTING, "优先提取 PDF 原生文本中");

        try (PdfToBase64Converter.PdfPageImageSession pageImageSession = PdfToBase64Converter.openSession(fileBytes)) {
            for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                try {
                    String nativeText = nativeTextPages.get(pageIndex);
                    if (hasUsableNativeText(nativeText)) {
                        pageContent.put(pageIndex, nativeText.trim());
                        nativePageCount++;
                        successPages++;
                        updateProcessProgress(pageIndex + 1, totalPages);
                        continue;
                    }

                    if (ocrModel == null) {
                        updateProcessingStage(STAGE_OCR_FALLBACK, "原生文本不足，正在按页 OCR 补全");
                        ocrModel = createOcrModelFromMessage(ragDocSyncOcrMessage);
                    }

                    String base64 = pageImageSession.renderPageToBase64(pageIndex, "jpg", renderDpi);
                    UserMessage userMessage = UserMessage.userMessage(
                            ImageContent.from(base64, TikaFileTypeDetector.detectFileType(Base64.decode(base64))),
                            TextContent.from(OCR_PROMPT));

                    ChatResponse chat = executeOcrWithTimeout(ocrModel, userMessage);
                    String ocrText = processText(chat.aiMessage().text());
                    if (StringUtils.hasText(ocrText)) {
                        pageContent.put(pageIndex, ocrText);
                        successPages++;
                    }
                    updateProcessProgress(pageIndex + 1, totalPages);
                } catch (TimeoutException e) {
                    log.error("OCR timed out for file {} page {}/{} after {}s", getCurrentProcessingFileId(),
                            pageIndex + 1, totalPages, OCR_PAGE_TIMEOUT_SECONDS);
                } catch (Exception e) {
                    log.error("Failed to process PDF page {}/{} for file {}: {}", pageIndex + 1, totalPages,
                            getCurrentProcessingFileId(), e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw new BusinessException("Failed to open PDF for OCR rendering: " + e.getMessage(), e);
        }

        if (successPages == 0) {
            throw new BusinessException("PDF processing failed for all pages");
        }

        log.info("PDF processing completed for file {}. Native text pages: {}, OCR pages: {}",
                getCurrentProcessingFileId(), nativePageCount, successPages - nativePageCount);
        return pageContent;
    }

    @Override
    public void insertData(RagDocMessage ragDocSyncOcrMessage, Map<Integer, String> ocrData) {
        updateProcessingStage(STAGE_SEGMENTING, "文本提取完成，正在整理分段");
        for (int pageIndex = 0; pageIndex < ragDocSyncOcrMessage.getPageSize(); pageIndex++) {
            String content = ocrData.getOrDefault(pageIndex, null);
            List<ProcessedSegment> segments = splitPageContent(content);
            if (segments.isEmpty()) {
                DocumentUnitEntity emptyUnit = new DocumentUnitEntity();
                emptyUnit.setContent(content);
                emptyUnit.setPage(pageIndex);
                emptyUnit.setFileId(ragDocSyncOcrMessage.getFileId());
                emptyUnit.setSourcePage(pageIndex);
                emptyUnit.setSegmentOrder(pageIndex);
                emptyUnit.setSegmentType(SegmentType.TEXT.getValue());
                emptyUnit.setIsVector(false);
                emptyUnit.setIsOcr(false);
                documentUnitRepository.checkInsert(emptyUnit);
                continue;
            }

            for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
                ProcessedSegment segment = segments.get(segmentIndex);
                if (segment.getOrder() == 0 && segmentIndex > 0) {
                    segment.setOrder(segmentIndex);
                }

                DocumentUnitEntity documentUnit = new DocumentUnitEntity();
                documentUnit.setContent(segment.getContent());
                documentUnit.setPage(pageIndex);
                documentUnit.setFileId(ragDocSyncOcrMessage.getFileId());
                documentUnit.setIsVector(false);
                documentUnit.setIsOcr(true);
                DocumentUnitMetadataSupport.apply(documentUnit, ensurePersistedSegment(segment, pageIndex), pageIndex);
                documentUnitRepository.checkInsert(documentUnit);
            }
        }
    }

    private Map<Integer, String> extractTextByPage(byte[] fileBytes, int totalPages) {
        Map<Integer, String> textByPage = new HashMap<>();
        try (PDDocument document = Loader.loadPDF(fileBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                stripper.setStartPage(pageIndex + 1);
                stripper.setEndPage(pageIndex + 1);
                String extracted = processText(stripper.getText(document));
                if (StringUtils.hasText(extracted)) {
                    textByPage.put(pageIndex, extracted);
                }
            }
        } catch (Exception e) {
            log.warn("Native PDF text extraction failed for file {}: {}", getCurrentProcessingFileId(), e.getMessage());
        }
        return textByPage;
    }

    private List<ProcessedSegment> splitPageContent(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        return structuredPlainTextProcessor.process(content);
    }

    private ProcessedSegment ensurePersistedSegment(ProcessedSegment segment, int order) {
        if (segment == null) {
            ProcessedSegment fallback = new ProcessedSegment(null, SegmentType.TEXT, null);
            fallback.setOrder(order);
            return fallback;
        }
        if (!StringUtils.hasText(segment.getContent())) {
            segment.setOrder(order);
            return segment;
        }
        if (segment.getOrder() < 0) {
            segment.setOrder(order);
        }
        if (!StringUtils.hasText(DocumentUnitMetadataSupport.inferTitlePath(segment.getContent()))
                && !StringUtils.hasText(
                        segment.getMetadata() != null ? String.valueOf(segment.getMetadata().get("TITLE_PATH")) : null)
                && segment.getType() == null) {
            segment.setType(SegmentType.TEXT);
        }
        return segment;
    }

    public String processText(String input) {
        if (input == null) {
            return null;
        }
        String result = input;
        result = PATTERNS[0].matcher(result).replaceAll(Matcher.quoteReplacement("\\("));
        result = PATTERNS[1].matcher(result).replaceAll(Matcher.quoteReplacement("\\)"));
        result = PATTERNS[2].matcher(result).replaceAll("\n\n");
        result = PATTERNS[3].matcher(result).replaceAll("$1\n$2");
        result = PATTERNS[4].matcher(result).replaceAll(Matcher.quoteReplacement("$"));
        result = PATTERNS[5].matcher(result).replaceAll(Matcher.quoteReplacement("$"));
        result = PATTERNS[6].matcher(result).replaceAll(Matcher.quoteReplacement("$$"));
        return result.trim();
    }

    private void updateProcessProgress(int currentPage, int totalPages) {
        String fileId = getCurrentProcessingFileId();
        if (fileId == null) {
            return;
        }

        try {
            double progress = (double) currentPage / totalPages * 100.0;
            LambdaUpdateWrapper<FileDetailEntity> wrapper = Wrappers.<FileDetailEntity>lambdaUpdate()
                    .eq(FileDetailEntity::getId, fileId).set(FileDetailEntity::getCurrentOcrPageNumber, currentPage)
                    .set(FileDetailEntity::getOcrProcessProgress, progress);
            fileDetailRepository.update(wrapper);
        } catch (Exception e) {
            log.warn("Failed to update OCR progress for file {}: {}", fileId, e.getMessage());
        }
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
            log.warn("Failed to update processing stage for file {}: {}", fileId, e.getMessage());
        }
    }

    private ChatModel createOcrModelFromMessage(RagDocMessage ragDocSyncOcrMessage) {
        if (ragDocSyncOcrMessage == null || ragDocSyncOcrMessage.getOcrModelConfig() == null) {
            String errorMsg = String.format("User %s is missing OCR model config",
                    ragDocSyncOcrMessage != null ? ragDocSyncOcrMessage.getUserId() : "unknown");
            log.error(errorMsg);
            throw new BusinessException(errorMsg);
        }

        try {
            var modelConfig = ragDocSyncOcrMessage.getOcrModelConfig();
            ProviderConfig ocrProviderConfig = new ProviderConfig(modelConfig.getApiKey(), modelConfig.getBaseUrl(),
                    modelConfig.getModelEndpoint(), ProviderProtocol.OPENAI);
            return LLMProviderService.getStrand(ProviderProtocol.OPENAI, ocrProviderConfig);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("Failed to create OCR model for user %s: %s",
                    ragDocSyncOcrMessage.getUserId(), e.getMessage());
            log.error(errorMsg, e);
            throw new BusinessException(errorMsg, e);
        }
    }

    private ChatResponse executeOcrWithTimeout(ChatModel ocrModel, UserMessage userMessage) throws TimeoutException {
        Future<ChatResponse> task = ocrTaskExecutor.submit(() -> ocrModel.chat(userMessage));
        try {
            return task.get(OCR_PAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            task.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            throw new BusinessException("OCR task was interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new BusinessException("OCR task failed", cause);
        }
    }

    private boolean hasUsableNativeText(String nativeText) {
        if (!StringUtils.hasText(nativeText)) {
            return false;
        }
        String normalized = nativeText.trim();
        if (normalized.length() >= MIN_TEXT_LENGTH_FOR_NATIVE_PDF) {
            return true;
        }
        long visibleCharCount = normalized.chars().filter(ch -> !Character.isWhitespace(ch)).count();
        return visibleCharCount >= MIN_TEXT_LENGTH_FOR_NATIVE_PDF;
    }

    private float resolveRenderDpi(byte[] fileBytes, int totalPages) {
        long fileSize = fileBytes != null ? fileBytes.length : 0;
        if (fileSize > 3L * 1024 * 1024 || totalPages > 25) {
            return ocrRenderDpiLarge;
        }
        return ocrRenderDpi;
    }

    private String getCurrentProcessingFileId() {
        return currentProcessingFileId.get();
    }
}
