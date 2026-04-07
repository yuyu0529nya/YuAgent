package org.xhy.domain.rag.service;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.xhy.domain.rag.constant.MetadataConstant;
import org.xhy.domain.rag.constant.SearchType;
import org.xhy.domain.rag.message.RagDocSyncStorageMessage;
import org.xhy.domain.rag.model.DocumentUnitEntity;
import org.xhy.domain.rag.model.VectorStoreResult;
import org.xhy.domain.rag.repository.DocumentUnitRepository;
import org.xhy.infrastructure.exception.BusinessException;
import org.xhy.infrastructure.rag.factory.EmbeddingModelFactory;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;

/** Domain service for vector search and document embedding. */
@Component
public class EmbeddingDomainService implements MetadataConstant {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingDomainService.class);

    private final EmbeddingModelFactory embeddingModelFactory;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final DocumentUnitRepository documentUnitRepository;

    public EmbeddingDomainService(EmbeddingModelFactory embeddingModelFactory,
            @Qualifier("initEmbeddingStore") EmbeddingStore<TextSegment> embeddingStore,
            DocumentUnitRepository documentUnitRepository) {
        this.embeddingModelFactory = embeddingModelFactory;
        this.embeddingStore = embeddingStore;
        this.documentUnitRepository = documentUnitRepository;
    }

    public List<VectorStoreResult> vectorSearch(List<String> dataSetIds, String question, Integer maxResults,
            Double minScore, Boolean enableRerank, Integer candidateMultiplier,
            EmbeddingModelFactory.EmbeddingConfig embeddingConfig) {
        if (dataSetIds == null || dataSetIds.isEmpty() || !StringUtils.hasText(question) || embeddingConfig == null) {
            return Collections.emptyList();
        }

        int finalMaxResults = maxResults != null ? Math.min(maxResults, 100) : 15;
        double finalMinScore = minScore != null ? Math.max(0.0, Math.min(minScore, 1.0)) : 0.7;
        boolean finalEnableRerank = enableRerank != null ? enableRerank : true;
        int finalCandidateMultiplier = candidateMultiplier != null ? Math.max(1, Math.min(candidateMultiplier, 5)) : 2;
        long startTime = System.currentTimeMillis();

        try {
            OpenAiEmbeddingModel embeddingModel = embeddingModelFactory.createEmbeddingModel(embeddingConfig);
            int searchLimit = finalEnableRerank
                    ? Math.max(finalMaxResults * finalCandidateMultiplier, 30)
                    : finalMaxResults;

            Embedding queryEmbedding = Embedding.from(embeddingModel.embed(question).content().vector());
            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(EmbeddingSearchRequest.builder()
                    .filter(new IsIn(DATA_SET_ID, dataSetIds)).maxResults(searchLimit).minScore(finalMinScore)
                    .queryEmbedding(queryEmbedding).build());

            List<EmbeddingMatch<TextSegment>> embeddingMatches = searchResult.matches();
            if (embeddingMatches.isEmpty() && finalMinScore > 0.3) {
                EmbeddingSearchResult<TextSegment> fallbackResult = embeddingStore.search(EmbeddingSearchRequest
                        .builder().filter(new IsIn(DATA_SET_ID, dataSetIds)).maxResults(searchLimit).minScore(0.3)
                        .queryEmbedding(queryEmbedding).build());
                embeddingMatches = fallbackResult.matches();
            }

            List<VectorStoreResult> results = embeddingMatches.stream().limit(finalMaxResults).map(match -> {
                VectorStoreResult result = new VectorStoreResult();
                result.setEmbeddingId(match.embeddingId());
                result.setText(match.embedded().text());
                result.setMetadata(match.embedded().metadata().toMap());
                result.setScore(match.score());
                result.setSearchType(SearchType.VECTOR);
                return result;
            }).toList();

            log.info("Vector search finished, datasets={}, results={}, cost={}ms", dataSetIds.size(), results.size(),
                    System.currentTimeMillis() - startTime);
            return results;
        } catch (Exception e) {
            log.error("Vector search failed after {}ms", System.currentTimeMillis() - startTime, e);
            return Collections.emptyList();
        }
    }

    public void deleteEmbedding(List<String> fileIds) {
        embeddingStore.removeAll(metadataKey(MetadataConstant.FILE_ID).isIn(fileIds));
    }

    /** Vectorize a single unit or a batch of units with one model instance. */
    public int syncStorage(RagDocSyncStorageMessage message) {
        OpenAiEmbeddingModel embeddingModel = createEmbeddingModelFromMessage(message);

        if (message.isBatchMessage()) {
            int successCount = 0;
            for (RagDocSyncStorageMessage.BatchUnit batchUnit : message.getBatchUnits()) {
                RagDocSyncStorageMessage unitMessage = copyBatchUnit(message, batchUnit);
                if (syncSingleStorage(unitMessage, embeddingModel)) {
                    successCount++;
                }
            }
            return successCount;
        }

        return syncSingleStorage(message, embeddingModel) ? 1 : 0;
    }

    private RagDocSyncStorageMessage copyBatchUnit(RagDocSyncStorageMessage batchMessage,
            RagDocSyncStorageMessage.BatchUnit batchUnit) {
        RagDocSyncStorageMessage unitMessage = new RagDocSyncStorageMessage();
        unitMessage.setId(batchUnit.getId());
        unitMessage.setFileId(batchMessage.getFileId());
        unitMessage.setPage(batchUnit.getPage());
        unitMessage.setContent(batchUnit.getContent());
        unitMessage.setVector(batchMessage.getVector());
        unitMessage.setFileName(batchMessage.getFileName());
        unitMessage.setDatasetId(batchMessage.getDatasetId());
        unitMessage.setUserId(batchMessage.getUserId());
        unitMessage.setEmbeddingModelConfig(batchMessage.getEmbeddingModelConfig());
        return unitMessage;
    }

    private boolean syncSingleStorage(RagDocSyncStorageMessage message, OpenAiEmbeddingModel embeddingModel) {
        if (!StringUtils.hasText(message.getContent())) {
            log.warn("Skip empty vectorization content for unit {}", message.getId());
            return false;
        }

        Metadata metadata = buildMetadata(message);
        TextSegment textSegment = new TextSegment(message.getContent(), metadata);
        Embedding embedding = embeddingModel.embed(textSegment).content();
        embeddingStore.add(embedding, textSegment);

        String originalDocId = extractOriginalDocId(message.getId());
        if (originalDocId != null) {
            documentUnitRepository.update(Wrappers.lambdaUpdate(DocumentUnitEntity.class)
                    .eq(DocumentUnitEntity::getId, originalDocId).set(DocumentUnitEntity::getIsVector, true));
        }
        return true;
    }

    private String extractOriginalDocId(String vectorId) {
        if (vectorId == null) {
            return null;
        }
        if (vectorId.contains("_segment_")) {
            return vectorId.substring(0, vectorId.indexOf("_segment_"));
        }
        return vectorId;
    }

    private Metadata buildMetadata(RagDocSyncStorageMessage message) {
        Metadata metadata = new Metadata();
        metadata.put(FILE_ID, message.getFileId());
        metadata.put(FILE_NAME, message.getFileName());
        metadata.put(DOCUMENT_ID, extractOriginalDocId(message.getId()));
        metadata.put(DATA_SET_ID, message.getDatasetId());
        return metadata;
    }

    private OpenAiEmbeddingModel createEmbeddingModelFromMessage(RagDocSyncStorageMessage message) {
        if (message == null || message.getEmbeddingModelConfig() == null) {
            String errorMsg = String.format("User %s is missing embedding model config",
                    message != null ? message.getUserId() : "unknown");
            throw new BusinessException(errorMsg);
        }

        try {
            var modelConfig = message.getEmbeddingModelConfig();
            EmbeddingModelFactory.EmbeddingConfig config = new EmbeddingModelFactory.EmbeddingConfig(
                    modelConfig.getApiKey(), modelConfig.getBaseUrl(), modelConfig.getModelEndpoint());
            OpenAiEmbeddingModel embeddingModel = embeddingModelFactory.createEmbeddingModel(config);
            log.info("Prepared embedding model for user {}, endpoint {}", message.getUserId(),
                    modelConfig.getModelEndpoint());
            return embeddingModel;
        } catch (Exception e) {
            String errorMsg = String.format("Failed to prepare embedding model for user %s: %s", message.getUserId(),
                    e.getMessage());
            log.error(errorMsg, e);
            throw new BusinessException(errorMsg, e);
        }
    }
}
