package org.yu.domain.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.yu.domain.rag.model.DocumentUnitEntity;
import org.yu.domain.rag.model.VectorStoreResult;
import org.yu.domain.rag.repository.DocumentUnitRepository;

class HybridSearchDomainServiceTest {

    @Test
    void returnsACompletedSearchResultEvenWhenTheSharedDeadlineHasPassed() {
        List<VectorStoreResult> expected = List.of(new VectorStoreResult());
        CompletableFuture<List<VectorStoreResult>> completed = CompletableFuture.completedFuture(expected);

        List<VectorStoreResult> result = HybridSearchDomainService.awaitSearchResult(completed, System.nanoTime() - 1,
                "test search");

        assertEquals(expected, result);
    }

    @Test
    void cancelsAnUnfinishedSearchAfterTheSharedDeadline() {
        CompletableFuture<List<VectorStoreResult>> unfinished = new CompletableFuture<>();

        List<VectorStoreResult> result = HybridSearchDomainService.awaitSearchResult(unfinished, System.nanoTime() - 1,
                "test search");

        assertTrue(result.isEmpty());
        assertTrue(unfinished.isCancelled());
    }

    @Test
    void keepsOriginalQuestionWhenBuildingVectorQuery() {
        assertEquals("原始问题\n假设文档", HybridSearchDomainService.buildVectorQuery("原始问题", "假设文档"));
        assertEquals("原始问题", HybridSearchDomainService.buildVectorQuery("原始问题", "原始问题"));
        assertEquals("原始问题", HybridSearchDomainService.buildVectorQuery("原始问题", null));
    }

    @Test
    void expandsAdjacentChunksWithOneBatchQuery() {
        DocumentUnitRepository repository = Mockito.mock(DocumentUnitRepository.class);
        HybridSearchDomainService service = new HybridSearchDomainService(Mockito.mock(EmbeddingDomainService.class),
                Mockito.mock(KeywordSearchDomainService.class), repository, Mockito.mock(RerankDomainService.class),
                Mockito.mock(HyDEDomainService.class));
        DocumentUnitEntity sourceA = document("source-a", "file-a", 5);
        DocumentUnitEntity sourceB = document("source-b", "file-b", 12);
        DocumentUnitEntity adjacentA = document("adjacent-a", "file-a", 4);
        DocumentUnitEntity adjacentB = document("adjacent-b", "file-b", 13);
        DocumentUnitEntity unrelatedPage = document("unrelated-page", "file-a", 12);

        when(repository.selectList(Mockito.<LambdaQueryWrapper<DocumentUnitEntity>>any()))
                .thenReturn(List.of(adjacentA, adjacentB, unrelatedPage));

        List<DocumentUnitEntity> result = service.expandQueryResults(new ArrayList<>(List.of(sourceA, sourceB)),
                Map.of("source-a", 0.9, "source-b", 0.8));

        assertEquals(List.of("source-a", "source-b", "adjacent-a", "adjacent-b"),
                result.stream().map(DocumentUnitEntity::getId).toList());
        assertEquals(0.72, adjacentA.getSimilarityScore(), 0.0001);
        assertEquals(0.64, adjacentB.getSimilarityScore(), 0.0001);
        verify(repository, times(1)).selectList(Mockito.<LambdaQueryWrapper<DocumentUnitEntity>>any());
    }

    private DocumentUnitEntity document(String id, String fileId, int page) {
        DocumentUnitEntity document = new DocumentUnitEntity();
        document.setId(id);
        document.setFileId(fileId);
        document.setPage(page);
        return document;
    }
}
