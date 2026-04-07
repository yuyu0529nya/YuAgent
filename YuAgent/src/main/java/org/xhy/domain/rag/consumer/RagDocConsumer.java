package org.xhy.domain.rag.consumer;

import static org.xhy.infrastructure.mq.core.MessageHeaders.TRACE_ID;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.xhy.domain.rag.constant.FileProcessingStatusEnum;
import org.xhy.domain.rag.message.RagDocMessage;
import org.xhy.domain.rag.message.RagDocSyncStorageMessage;
import org.xhy.domain.rag.model.DocumentUnitEntity;
import org.xhy.domain.rag.model.FileDetailEntity;
import org.xhy.domain.rag.repository.DocumentUnitRepository;
import org.xhy.domain.rag.service.FileDetailDomainService;
import org.xhy.domain.rag.strategy.DocumentProcessingStrategy;
import org.xhy.domain.rag.strategy.context.DocumentProcessingFactory;
import org.xhy.infrastructure.exception.BusinessException;
import org.xhy.infrastructure.mq.core.MessageEnvelope;
import org.xhy.infrastructure.mq.core.MessagePublisher;
import org.xhy.infrastructure.mq.enums.EventType;
import org.xhy.infrastructure.mq.events.RagDocSyncOcrEvent;
import org.xhy.infrastructure.mq.events.RagDocSyncStorageEvent;
import org.xhy.infrastructure.rag.service.UserModelConfigResolver;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;

/** Handles OCR processing and auto-starts vectorization. */
@RabbitListener(bindings = @QueueBinding(value = @Queue(RagDocSyncOcrEvent.QUEUE_NAME), exchange = @Exchange(value = RagDocSyncOcrEvent.EXCHANGE_NAME, type = ExchangeTypes.TOPIC), key = RagDocSyncOcrEvent.ROUTE_KEY))
@Component
public class RagDocConsumer {

    private static final Logger log = LoggerFactory.getLogger(RagDocConsumer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final DocumentProcessingFactory documentProcessingFactory;
    private final FileDetailDomainService fileDetailDomainService;
    private final DocumentUnitRepository documentUnitRepository;
    private final MessagePublisher messagePublisher;
    private final UserModelConfigResolver userModelConfigResolver;

    public RagDocConsumer(DocumentProcessingFactory documentProcessingFactory,
            FileDetailDomainService fileDetailDomainService, DocumentUnitRepository documentUnitRepository,
            MessagePublisher messagePublisher, UserModelConfigResolver userModelConfigResolver) {
        this.documentProcessingFactory = documentProcessingFactory;
        this.fileDetailDomainService = fileDetailDomainService;
        this.documentUnitRepository = documentUnitRepository;
        this.messagePublisher = messagePublisher;
        this.userModelConfigResolver = userModelConfigResolver;
    }

    @RabbitHandler
    public void receiveMessage(java.util.Map<String, Object> payload, Message message, Channel channel)
            throws IOException {
        RagDocMessage docMessage = null;
        long startTime = System.currentTimeMillis();

        try {
            MessageEnvelope<RagDocMessage> envelope = OBJECT_MAPPER.convertValue(payload, new TypeReference<>() {
            });

            MDC.put(TRACE_ID, Objects.nonNull(envelope.getTraceId()) ? envelope.getTraceId() : IdWorker.getTimeId());
            docMessage = envelope.getData();

            log.info("Start OCR for file {}", docMessage.getFileId());

            FileDetailEntity fileEntity = fileDetailDomainService.getFileByIdWithoutUserCheck(docMessage.getFileId());
            boolean startSuccess = fileDetailDomainService.startFileOcrProcessing(docMessage.getFileId(),
                    fileEntity.getUserId());

            if (!startSuccess && canResumeOcr(fileEntity, docMessage.getFileId())) {
                log.warn("Resume OCR for file {} from existing OCR_PROCESSING state", docMessage.getFileId());
                startSuccess = true;
            }

            if (!startSuccess) {
                throw new BusinessException("Failed to start OCR processing");
            }

            String fileExt = fileDetailDomainService.getFileExtension(docMessage.getFileId());
            if (fileExt == null) {
                throw new BusinessException("File extension not found");
            }

            DocumentProcessingStrategy strategy = documentProcessingFactory.getDocumentStrategyHandler(fileExt.toUpperCase());
            if (strategy == null) {
                throw new BusinessException("Unsupported file extension: " + fileExt);
            }

            long ocrStartTime = System.currentTimeMillis();
            strategy.handle(docMessage, fileExt.toUpperCase());
            long ocrCost = System.currentTimeMillis() - ocrStartTime;

            fileEntity = fileDetailDomainService.getFileByIdWithoutUserCheck(docMessage.getFileId());
            Integer totalPages = normalizeFilePageSize(docMessage.getFileId(), fileEntity.getFilePageSize());
            fileEntity.setFilePageSize(totalPages);
            if (totalPages != null && totalPages > 0) {
                fileDetailDomainService.updateFileOcrProgress(docMessage.getFileId(), totalPages, totalPages,
                        fileEntity.getUserId());
            }

            boolean completeSuccess = fileDetailDomainService.completeFileOcrProcessing(docMessage.getFileId(),
                    fileEntity.getUserId());
            if (!completeSuccess) {
                log.warn("Failed to move OCR state to completed for file {}", docMessage.getFileId());
            }

            log.info("OCR completed for file {}, pages={}, cost={}ms", docMessage.getFileId(),
                    totalPages != null ? totalPages : 0, ocrCost);

            autoStartVectorization(docMessage.getFileId(), fileEntity);
            log.info("Upload processing chain advanced for file {}, totalCost={}ms", docMessage.getFileId(),
                    System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("OCR failed for file {}, cost={}ms", docMessage != null ? docMessage.getFileId() : "unknown",
                    System.currentTimeMillis() - startTime, e);
            try {
                if (docMessage != null) {
                    FileDetailEntity fileEntity = fileDetailDomainService.getFileByIdWithoutUserCheck(docMessage.getFileId());
                    fileDetailDomainService.failFileOcrProcessing(docMessage.getFileId(), fileEntity.getUserId());
                }
            } catch (Exception ex) {
                log.error("Failed to mark OCR as failed for file {}", docMessage != null ? docMessage.getFileId() : "unknown",
                        ex);
            }
        }
    }

    private void autoStartVectorization(String fileId, FileDetailEntity fileEntity) {
        long startTime = System.currentTimeMillis();
        try {
            List<DocumentUnitEntity> documentUnits = findVectorizableDocumentUnits(fileId);
            if (documentUnits.isEmpty()) {
                log.warn("No vectorizable document units found for file {}", fileId);
                return;
            }

            boolean startSuccess = fileDetailDomainService.startFileEmbeddingProcessing(fileId, fileEntity.getUserId());
            if (!startSuccess) {
                log.warn("Failed to start embedding processing for file {}", fileId);
                return;
            }

            RagDocSyncStorageMessage storageMessage = buildBatchStorageMessage(fileEntity, documentUnits);
            MessageEnvelope<RagDocSyncStorageMessage> env = MessageEnvelope.builder(storageMessage)
                    .addEventType(EventType.DOC_SYNC_RAG)
                    .description("Batch vectorization for file " + fileId + ", units=" + documentUnits.size()).build();
            messagePublisher.publish(RagDocSyncStorageEvent.route(), env);

            log.info("Queued batch vectorization for file {}, units={}, cost={}ms", fileId, documentUnits.size(),
                    System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Failed to auto-start vectorization for file {}", fileId, e);
            try {
                fileDetailDomainService.failFileEmbeddingProcessing(fileId, fileEntity.getUserId());
            } catch (Exception ex) {
                log.error("Failed to mark embedding as failed for file {}", fileId, ex);
            }
        }
    }

    private RagDocSyncStorageMessage buildBatchStorageMessage(FileDetailEntity fileEntity,
            List<DocumentUnitEntity> documentUnits) {
        RagDocSyncStorageMessage storageMessage = new RagDocSyncStorageMessage();
        storageMessage.setId(fileEntity.getId());
        storageMessage.setFileId(fileEntity.getId());
        storageMessage.setFileName(fileEntity.getOriginalFilename());
        storageMessage.setVector(true);
        storageMessage.setDatasetId(fileEntity.getDataSetId());
        storageMessage.setUserId(fileEntity.getUserId());
        storageMessage.setEmbeddingModelConfig(userModelConfigResolver.getUserEmbeddingModelConfig(fileEntity.getUserId()));
        storageMessage.setBatchUnits(documentUnits.stream().map(this::toBatchUnit).toList());
        return storageMessage;
    }

    private RagDocSyncStorageMessage.BatchUnit toBatchUnit(DocumentUnitEntity documentUnit) {
        RagDocSyncStorageMessage.BatchUnit batchUnit = new RagDocSyncStorageMessage.BatchUnit();
        batchUnit.setId(documentUnit.getId());
        batchUnit.setPage(documentUnit.getPage());
        batchUnit.setContent(documentUnit.getContent());
        return batchUnit;
    }

    private List<DocumentUnitEntity> findVectorizableDocumentUnits(String fileId) {
        List<DocumentUnitEntity> ocrReadyUnits = documentUnitRepository
                .selectList(Wrappers.lambdaQuery(DocumentUnitEntity.class).eq(DocumentUnitEntity::getFileId, fileId)
                        .eq(DocumentUnitEntity::getIsOcr, true).eq(DocumentUnitEntity::getIsVector, false));

        if (!ocrReadyUnits.isEmpty()) {
            return ocrReadyUnits;
        }

        List<DocumentUnitEntity> fallbackUnits = documentUnitRepository
                .selectList(Wrappers.lambdaQuery(DocumentUnitEntity.class).eq(DocumentUnitEntity::getFileId, fileId)
                        .eq(DocumentUnitEntity::getIsVector, false));

        return fallbackUnits.stream().filter(unit -> StringUtils.hasText(unit.getContent())).toList();
    }

    private Integer normalizeFilePageSize(String fileId, Integer currentPageSize) {
        if (currentPageSize != null && currentPageSize > 0) {
            return currentPageSize;
        }

        long documentUnitCount = documentUnitRepository.selectCount(Wrappers.lambdaQuery(DocumentUnitEntity.class)
                .eq(DocumentUnitEntity::getFileId, fileId));
        int normalizedPageSize = documentUnitCount > 0 ? (int) documentUnitCount : 0;
        fileDetailDomainService.updateFilePageSize(fileId, normalizedPageSize);
        log.warn("file_page_size was empty for file {}, backfilled with {}", fileId, normalizedPageSize);
        return normalizedPageSize;
    }

    private boolean canResumeOcr(FileDetailEntity fileEntity, String fileId) {
        if (!FileProcessingStatusEnum.OCR_PROCESSING.getCode().equals(fileEntity.getProcessingStatus())) {
            return false;
        }
        boolean noProgress = (fileEntity.getCurrentOcrPageNumber() == null || fileEntity.getCurrentOcrPageNumber() == 0)
                && (fileEntity.getOcrProcessProgress() == null || fileEntity.getOcrProcessProgress() == 0D);
        if (!noProgress) {
            return false;
        }

        long existingUnits = documentUnitRepository.selectCount(Wrappers.lambdaQuery(DocumentUnitEntity.class)
                .eq(DocumentUnitEntity::getFileId, fileId));
        return existingUnits == 0;
    }
}
