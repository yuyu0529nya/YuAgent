package org.yu.application.agent.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yu.application.agent.assembler.AgentWidgetAssembler;
import org.yu.application.agent.dto.AgentWidgetDTO;
import org.yu.domain.agent.model.AgentWidgetEntity;
import org.yu.domain.agent.repository.AgentRepository;
import org.yu.domain.agent.service.AgentWidgetDomainService;
import org.yu.domain.agent.service.AgentWidgetUsageDomainService;
import org.yu.domain.llm.model.ModelEntity;
import org.yu.domain.llm.model.ProviderEntity;
import org.yu.domain.llm.service.LLMDomainService;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentWidgetAppServiceTest {

    @Mock
    private AgentWidgetDomainService agentWidgetDomainService;
    @Mock
    private AgentRepository agentRepository;
    @Mock
    private LLMDomainService llmDomainService;
    @Mock
    private AgentWidgetUsageDomainService agentWidgetUsageDomainService;

    @Test
    void getWidgetsByUserLoadsModelsAndProvidersInBatches() {
        AgentWidgetEntity first = widget("widget-1", "model-1");
        AgentWidgetEntity second = widget("widget-2", "model-2");
        ModelEntity firstModel = model("model-1", "provider-1");
        ModelEntity secondModel = model("model-2", "provider-1");
        ProviderEntity provider = provider("provider-1");
        when(agentWidgetDomainService.getWidgetsByUser("user-1")).thenReturn(List.of(first, second));
        when(llmDomainService.getModelsByIds(anySet())).thenReturn(List.of(firstModel, secondModel));
        when(llmDomainService.getProvidersByIds(anySet())).thenReturn(List.of(provider));
        when(agentWidgetUsageDomainService.getTodayCallCounts(anyList()))
                .thenReturn(Map.of("widget-1", 3, "widget-2", 5));

        AgentWidgetAssembler assembler = new AgentWidgetAssembler();
        assembler.frontendBaseUrl = "https://example.test";
        AgentWidgetAppService service = new AgentWidgetAppService(agentWidgetDomainService, agentRepository,
                llmDomainService, assembler, agentWidgetUsageDomainService);

        List<AgentWidgetDTO> result = service.getWidgetsByUser("user-1");

        assertEquals(List.of(3, 5), result.stream().map(AgentWidgetDTO::getDailyCalls).toList());
        ArgumentCaptor<Set<String>> modelIds = ArgumentCaptor.forClass(Set.class);
        ArgumentCaptor<Set<String>> providerIds = ArgumentCaptor.forClass(Set.class);
        verify(llmDomainService).getModelsByIds(modelIds.capture());
        verify(llmDomainService).getProvidersByIds(providerIds.capture());
        assertEquals(Set.of("model-1", "model-2"), modelIds.getValue());
        assertEquals(Set.of("provider-1"), providerIds.getValue());
        verify(llmDomainService, never()).getModelById("model-1");
        verify(llmDomainService, never()).getProvider("provider-1");
    }

    private AgentWidgetEntity widget(String id, String modelId) {
        AgentWidgetEntity widget = new AgentWidgetEntity();
        widget.setId(id);
        widget.setPublicId(id);
        widget.setModelId(modelId);
        return widget;
    }

    private ModelEntity model(String id, String providerId) {
        ModelEntity model = new ModelEntity();
        model.setId(id);
        model.setProviderId(providerId);
        return model;
    }

    private ProviderEntity provider(String id) {
        ProviderEntity provider = new ProviderEntity();
        provider.setId(id);
        return provider;
    }
}
