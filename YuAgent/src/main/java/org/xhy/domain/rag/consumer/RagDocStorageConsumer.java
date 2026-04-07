package org.xhy.domain.rag.consumer;

import static org.xhy.infrastructure.mq.core.MessageHeaders.TRACE_ID;

import java.io.IOException;
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
import org.xhy.domain.rag.message.RagDocSyncStorageMessage;
import org.xhy.domain.rag.model.DocumentUnitEntity;
import org.xhy.domain.rag.service.EmbeddingDomainService;
import org.xhy.domain.rag.service.FileDetailDomainService;
import org.xhy.infrastructure.mq.core.MessageEnvelope;
import org.xhy.infrastructure.mq.events.RagDocSyncStorageEvent;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;
import org.xhy.domain.rag.repository.DocumentUnitRepository;

/** Handles asynchronous vectorization tasks for document units. */
@RabbitListener(bindings = @QueueBinding(value = @Queue(RagDocSyncStorageEvent.QUEUE_NAME), exchange = @Exchange(value = RagDocSyncStorageEvent.EXCHANGE_NAME, type = ExchangeTypes.TOPIC), key = RagDocSyncStorageEvent.ROUTE_KEY))
@Component
public class RagDocStorageConsumer {

    private static final Logger log = LoggerFactory.getLogger(RagDocStorageConsumer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final EmbeddingDomainService embeddingService;
    private final FileDetailDomainService fileDetailDomainService;
    private final DocumentUnitRepository documentUnitRepository;

    public RagDocStorageConsumer(EmbeddingDomainService embeddingService,
            FileDetailDomainService fileDetailDomainService, DocumentUnitRepository documentUnitRepository) {
        this.embeddingService = embeddingService;
        this.fileDetailDomainService = fileDetailDomainService;
        this.documentUnitRepository = documentUnitRepository;
    }

    @RabbitHandler
    public void receiveMessage(java.util.Map<String, Object> payload, Message message, Channel channel)
            throws IOException {
        RagDocSyncStorageMessage storageMessage = null;
        long startTime = System.currentTimeMillis();

        try {
            MessageEnvelope<RagDocSyncStorageMessage> envelope = OBJECT_MAPPER.convertValue(payload,
                    new TypeReference<MessageEnvelope<RagDocSyncStorageMessage>>() {
                    });

            MDC.put(TRACE_ID, Objects.nonNull(envelope.getTraceId()) ? envelope.getTraceId() : IdWorker.getTimeId());
            storageMessage = envelope.getData();

            int unitCount = storageMessage.isBatchMessage() ? storageMessage.getBatchUnits().size() : 1;
            log.info("Start vectorization for file {}, units={}", storageMessage.getFileId(), unitCount);

            int successCount = embeddingService.syncStorage(storageMessage);
            updateEmbeddingProgress(storageMessage.getFileId(), successCount);

            log.info("Finished vectorization for file {}, successUnits={}, cost={}ms", storageMessage.getFileId(),
                    successCount, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Vectorization failed, fileId={}, unitId={}, cost={}ms",
                    storageMessage != null ? storageMessage.getFileId() : "unknown",
                    storageMessage != null ? storageMessage.getId() : "unknown",
                    System.currentTimeMillis() - startTime, e);

            if (storageMessage != null) {
                markEmbeddingFailed(storageMessage);
            }
        }
    }

    private void updateEmbeddingProgress(String fileId, int successCount) {
        try {
            var fileEntity = fileDetailDomainService.getFileByIdWithoutUserCheck(fileId);

            long totalVectorizableUnits = documentUnitRepository.selectCount(Wrappers.<DocumentUnitEntity>lambdaQuery()
                    .eq(DocumentUnitEntity::getFileId, fileId).eq(DocumentUnitEntity::getIsOcr, true));
            long completedVectorUnits = documentUnitRepository.selectCount(Wrappers.<DocumentUnitEntity>lambdaQuery()
                    .eq(DocumentUnitEntity::getFileId, fileId).eq(DocumentUnitEntity::getIsVector, true));

            if (totalVectorizableUnits <= 0) {
                totalVectorizableUnits = documentUnitRepository.selectCount(Wrappers.<DocumentUnitEntity>lambdaQuery()
                        .eq(DocumentUnitEntity::getFileId, fileId).eq(DocumentUnitEntity::getIsVector, false)
                        .isNotNull(DocumentUnitEntity::getContent));
            }

            if (totalVectorizableUnits <= 0) {
                log.warn("No vectorizable document units found for file {}", fileId);
                return;
            }

            int completed = (int) completedVectorUnits;
            int total = (int) totalVectorizableUnits;
            fileDetailDomainService.updateFileEmbeddingProgress(fileId, completed, total, fileEntity.getUserId());

            if (completed >= total) {
                fileDetailDomainService.completeFileEmbeddingProcessing(fileId, fileEntity.getUserId());
                log.info("File {} vectorization completed", fileId);
            } else {
                log.debug("Embedding progress updated for file {}: {}/{} (+{})", fileId, completed, total,
                        successCount);
            }
        } catch (Exception e) {
            log.warn("Failed to update embedding progress for file {}: {}", fileId, e.getMessage());
        }
    }

    private void markEmbeddingFailed(RagDocSyncStorageMessage message) {
        try {
            var fileEntity = fileDetailDomainService.getFileByIdWithoutUserCheck(message.getFileId());
            fileDetailDomainService.failFileEmbeddingProcessing(message.getFileId(), fileEntity.getUserId());
        } catch (Exception statusEx) {
            log.warn("Failed to mark embedding as failed for file {}", message.getFileId(), statusEx);
        }
    }
}
