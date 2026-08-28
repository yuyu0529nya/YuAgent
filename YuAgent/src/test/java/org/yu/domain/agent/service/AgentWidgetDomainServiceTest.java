package org.yu.domain.agent.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yu.domain.agent.model.AgentWidgetEntity;
import org.yu.domain.agent.repository.AgentWidgetRepository;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentWidgetDomainServiceTest {

    @Mock
    private AgentWidgetRepository agentWidgetRepository;

    @Test
    void createWidgetRegeneratesAndRechecksConflictingPublicId() {
        AgentWidgetDomainService service = new AgentWidgetDomainService(agentWidgetRepository);
        AgentWidgetEntity widget = new AgentWidgetEntity();
        widget.setPublicId("widget_conflict");

        when(agentWidgetRepository.exists(any())).thenReturn(true, false);

        AgentWidgetEntity result = service.createWidget(widget);

        assertSame(widget, result);
        assertNotEquals("widget_conflict", widget.getPublicId());
        verify(agentWidgetRepository, times(2)).exists(any());
        verify(agentWidgetRepository).insert(widget);
    }
}
