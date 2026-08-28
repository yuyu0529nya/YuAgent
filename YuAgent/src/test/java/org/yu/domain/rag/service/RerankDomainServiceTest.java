package org.yu.domain.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.yu.domain.rag.dto.req.RerankRequest;
import org.yu.domain.rag.dto.resp.RerankResponse;
import org.yu.infrastructure.rag.api.RerankForestApi;
import org.yu.infrastructure.rag.config.RerankProperties;

class RerankDomainServiceTest {

    @Test
    void shouldKeepEveryDuplicateDocumentInOriginalOrderWhenQueryIsBlank() {
        RerankForestApi api = Mockito.mock(RerankForestApi.class);
        RerankDomainService service = new RerankDomainService(properties(), api);

        assertEquals(List.of(0, 1, 2), service.rerank(List.of("same", "same", "other"), " "));

        verify(api, never()).rerank(Mockito.anyString(), Mockito.anyString(), any(RerankRequest.class));
    }

    @Test
    void shouldFallBackToOriginalOrderWhenRerankCallFails() {
        RerankForestApi api = Mockito.mock(RerankForestApi.class);
        when(api.rerank(Mockito.anyString(), Mockito.anyString(), any(RerankRequest.class)))
                .thenThrow(new RuntimeException("network unavailable"));
        RerankDomainService service = new RerankDomainService(properties(), api);

        assertEquals(List.of(0, 1, 2), service.rerank(List.of("same", "same", "other"), "question"));
    }

    @Test
    void shouldDiscardInvalidAndDuplicateIndicesThenAppendMissingDocuments() {
        RerankResponse.SearchResult valid = result(2);
        RerankResponse.SearchResult duplicate = result(2);
        RerankResponse.SearchResult negative = result(-1);
        RerankResponse.SearchResult outOfRange = result(3);
        RerankResponse response = new RerankResponse();
        response.setResults(List.of(valid, duplicate, negative, outOfRange));

        RerankForestApi api = Mockito.mock(RerankForestApi.class);
        when(api.rerank(Mockito.anyString(), Mockito.anyString(), any(RerankRequest.class))).thenReturn(response);
        RerankDomainService service = new RerankDomainService(properties(), api);

        assertEquals(List.of(2, 0, 1), service.rerank(List.of("first", "second", "third"), "question"));
        verify(api).rerank(Mockito.anyString(), Mockito.anyString(), any(RerankRequest.class));
    }

    private RerankProperties properties() {
        RerankProperties properties = new RerankProperties();
        properties.setApiUrl("https://rerank.example.com");
        properties.setApiKey("test-key");
        properties.setModel("test-model");
        return properties;
    }

    private RerankResponse.SearchResult result(int index) {
        RerankResponse.SearchResult result = new RerankResponse.SearchResult();
        result.setIndex(index);
        return result;
    }
}
