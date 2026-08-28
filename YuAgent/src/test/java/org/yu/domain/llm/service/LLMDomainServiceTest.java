package org.yu.domain.llm.service;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.yu.domain.llm.repository.ModelRepository;
import org.yu.domain.llm.repository.ProviderRepository;

class LLMDomainServiceTest {

    @Test
    void doesNotQueryForBlankModelId() {
        ModelRepository modelRepository = Mockito.mock(ModelRepository.class);
        LLMDomainService service = new LLMDomainService(Mockito.mock(ProviderRepository.class), modelRepository,
                Mockito.mock(ApplicationEventPublisher.class));

        assertNull(service.findModelById(" "));
        verify(modelRepository, never()).selectById(Mockito.any());
    }
}
