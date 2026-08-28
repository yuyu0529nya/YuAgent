package org.yu.domain.memory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.yu.domain.memory.model.CandidateMemory;
import org.yu.domain.memory.model.MemoryItemEntity;
import org.yu.domain.memory.model.MemoryType;
import org.yu.domain.rag.model.ModelConfig;
import org.yu.domain.memory.repository.MemoryItemRepository;
import org.yu.infrastructure.rag.factory.EmbeddingModelFactory;
import org.yu.infrastructure.rag.service.UserModelConfigResolver;

class MemoryDomainServiceTest {

    @Test
    void shouldCapPageSizeToAvoidOversizedResponses() {
        MemoryItemRepository repository = Mockito.mock(MemoryItemRepository.class);
        when(repository.selectPage(Mockito.<Page<MemoryItemEntity>>any(),
                Mockito.<LambdaQueryWrapper<MemoryItemEntity>>any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Page<MemoryItemEntity> page = service(repository).pageMemories("user-1", null, 0, 1_000);

        assertEquals(1, page.getCurrent());
        assertEquals(100, page.getSize());
    }

    @Test
    void shouldBatchMemoryDeduplicationLookupBeforePersistingCandidates() {
        MemoryItemRepository repository = Mockito.mock(MemoryItemRepository.class);
        EmbeddingModelFactory embeddingModelFactory = Mockito.mock(EmbeddingModelFactory.class);
        UserModelConfigResolver modelConfigResolver = Mockito.mock(UserModelConfigResolver.class);
        @SuppressWarnings("unchecked")
        EmbeddingStore<TextSegment> embeddingStore = Mockito.mock(EmbeddingStore.class);
        OpenAiEmbeddingModel embeddingModel = Mockito.mock(OpenAiEmbeddingModel.class);

        when(repository.selectList(Mockito.<LambdaQueryWrapper<MemoryItemEntity>>any())).thenReturn(List.of());
        when(repository.insert(Mockito.any(MemoryItemEntity.class))).thenAnswer(invocation -> {
            MemoryItemEntity item = invocation.getArgument(0);
            item.setId("memory-" + item.getText());
            return 1;
        });
        when(modelConfigResolver.getUserEmbeddingModelConfig("user-1")).thenReturn(new ModelConfig());
        when(embeddingModelFactory.createEmbeddingModel(Mockito.any())).thenReturn(embeddingModel);
        when(embeddingModel.embed(Mockito.any(TextSegment.class)))
                .thenReturn(Response.from(Embedding.from(new float[]{0.1f})));

        MemoryDomainService service = new MemoryDomainService(repository, embeddingModelFactory, modelConfigResolver,
                embeddingStore);
        List<String> ids = service.saveMemories("user-1", "session-1",
                List.of(candidate("长期偏好使用中文"), candidate("长期偏好使用英文")));

        assertEquals(List.of("memory-长期偏好使用中文", "memory-长期偏好使用英文"), ids);
        verify(repository, times(1)).selectList(Mockito.<LambdaQueryWrapper<MemoryItemEntity>>any());
        verify(repository, never()).selectOne(Mockito.<LambdaQueryWrapper<MemoryItemEntity>>any());
        verify(repository, times(2)).insert(Mockito.any(MemoryItemEntity.class));
    }

    @Test
    void shouldDeduplicateCandidatesThatOnlyDifferByWhitespace() {
        MemoryItemRepository repository = Mockito.mock(MemoryItemRepository.class);
        EmbeddingModelFactory embeddingModelFactory = Mockito.mock(EmbeddingModelFactory.class);
        UserModelConfigResolver modelConfigResolver = Mockito.mock(UserModelConfigResolver.class);
        @SuppressWarnings("unchecked")
        EmbeddingStore<TextSegment> embeddingStore = Mockito.mock(EmbeddingStore.class);
        OpenAiEmbeddingModel embeddingModel = Mockito.mock(OpenAiEmbeddingModel.class);

        when(repository.selectList(Mockito.<LambdaQueryWrapper<MemoryItemEntity>>any())).thenReturn(List.of());
        when(repository.insert(Mockito.any(MemoryItemEntity.class))).thenAnswer(invocation -> {
            MemoryItemEntity item = invocation.getArgument(0);
            item.setId("memory-1");
            return 1;
        });
        when(repository.updateById(Mockito.any(MemoryItemEntity.class))).thenReturn(1);
        when(modelConfigResolver.getUserEmbeddingModelConfig("user-1")).thenReturn(new ModelConfig());
        when(embeddingModelFactory.createEmbeddingModel(Mockito.any())).thenReturn(embeddingModel);
        when(embeddingModel.embed(Mockito.any(TextSegment.class)))
                .thenReturn(Response.from(Embedding.from(new float[]{0.1f})));

        MemoryDomainService service = new MemoryDomainService(repository, embeddingModelFactory, modelConfigResolver,
                embeddingStore);
        List<String> ids = service.saveMemories("user-1", "session-1",
                List.of(candidate("长期偏好\t使用中文"), candidate("长期偏好  使用中文")));

        assertEquals(List.of("memory-1", "memory-1"), ids);
        verify(repository, times(1)).insert(Mockito.any(MemoryItemEntity.class));
        verify(repository, times(1)).updateById(Mockito.any(MemoryItemEntity.class));
    }

    @Test
    void shouldKeepOnlyTheBestVectorMatchForEachMemoryItem() {
        MemoryItemRepository repository = Mockito.mock(MemoryItemRepository.class);
        EmbeddingModelFactory embeddingModelFactory = Mockito.mock(EmbeddingModelFactory.class);
        UserModelConfigResolver modelConfigResolver = Mockito.mock(UserModelConfigResolver.class);
        @SuppressWarnings("unchecked")
        EmbeddingStore<TextSegment> embeddingStore = Mockito.mock(EmbeddingStore.class);
        OpenAiEmbeddingModel embeddingModel = Mockito.mock(OpenAiEmbeddingModel.class);
        MemoryItemEntity item = new MemoryItemEntity();
        item.setId("memory-1");
        item.setType(MemoryType.FACT.name());
        item.setText("用户偏好中文回答");
        item.setImportance(0.8f);
        item.setStatus(1);

        when(modelConfigResolver.getUserEmbeddingModelConfig("user-1")).thenReturn(new ModelConfig());
        when(embeddingModelFactory.createEmbeddingModel(Mockito.any())).thenReturn(embeddingModel);
        when(embeddingModel.embed("中文偏好")).thenReturn(Response.from(Embedding.from(new float[]{0.1f})));
        when(repository.selectList(Mockito.<LambdaQueryWrapper<MemoryItemEntity>>any())).thenReturn(List.of(item));
        when(embeddingStore.search(Mockito.any()))
                .thenReturn(new EmbeddingSearchResult<>(List.of(match("memory-1", 0.6), match("memory-1", 0.9))));

        MemoryDomainService service = new MemoryDomainService(repository, embeddingModelFactory, modelConfigResolver,
                embeddingStore);
        var results = service.searchRelevant("user-1", "中文偏好", 5);

        assertEquals(1, results.size());
        assertEquals("memory-1", results.get(0).getItemId());
        assertEquals(0.87, results.get(0).getScore(), 0.0001);
        verify(repository, times(1)).selectList(Mockito.<LambdaQueryWrapper<MemoryItemEntity>>any());
    }

    private CandidateMemory candidate(String text) {
        CandidateMemory candidate = new CandidateMemory();
        candidate.setType(MemoryType.FACT);
        candidate.setText(text);
        return candidate;
    }

    private EmbeddingMatch<TextSegment> match(String itemId, double score) {
        Metadata metadata = new Metadata();
        metadata.put("ITEM_ID", itemId);
        TextSegment segment = new TextSegment("memory", metadata);
        return new EmbeddingMatch<>(score, "embedding-" + score, Embedding.from(new float[]{0.1f}), segment);
    }

    private MemoryDomainService service(MemoryItemRepository repository) {
        return new MemoryDomainService(repository, Mockito.mock(EmbeddingModelFactory.class),
                Mockito.mock(UserModelConfigResolver.class), Mockito.mock());
    }
}
