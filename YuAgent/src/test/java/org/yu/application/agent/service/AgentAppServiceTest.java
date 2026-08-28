package org.yu.application.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.yu.application.billing.service.BillingService;
import org.yu.application.agent.dto.AgentVersionDTO;
import org.yu.domain.agent.model.AgentEntity;
import org.yu.domain.agent.model.AgentVersionEntity;
import org.yu.domain.agent.service.AgentDomainService;
import org.yu.domain.agent.service.AgentWorkspaceDomainService;
import org.yu.domain.rag.service.management.RagVersionDomainService;
import org.yu.domain.rag.service.management.UserRagDomainService;
import org.yu.domain.scheduledtask.service.ScheduledTaskExecutionService;
import org.yu.domain.tool.service.UserToolDomainService;
import org.yu.infrastructure.exception.BusinessException;

class AgentAppServiceTest {

    @Test
    void shouldCheckOwnershipBeforeReturningAnAgentVersion() {
        AgentDomainService agentDomainService = Mockito.mock(AgentDomainService.class);
        AgentEntity agent = new AgentEntity();
        AgentVersionEntity version = new AgentVersionEntity();
        version.setId("version-1");
        version.setAgentId("agent-1");
        version.setVersionNumber("1.0.0");
        when(agentDomainService.getAgent("agent-1", "user-1")).thenReturn(agent);
        when(agentDomainService.getAgentVersion("agent-1", "1.0.0")).thenReturn(version);

        AgentVersionDTO result = service(agentDomainService).getAgentVersion("agent-1", "1.0.0", "user-1");

        assertEquals("version-1", result.getId());
        verify(agentDomainService).getAgent("agent-1", "user-1");
        verify(agentDomainService).getAgentVersion("agent-1", "1.0.0");
    }

    @Test
    void shouldNotReadVersionWhenOwnershipCheckFails() {
        AgentDomainService agentDomainService = Mockito.mock(AgentDomainService.class);
        when(agentDomainService.getAgent("foreign-agent", "user-1"))
                .thenThrow(new BusinessException("Agent不存在: foreign-agent"));

        assertThrows(BusinessException.class,
                () -> service(agentDomainService).getAgentVersion("foreign-agent", "1.0.0", "user-1"));
        verify(agentDomainService).getAgent("foreign-agent", "user-1");
        verify(agentDomainService, never()).getAgentVersion("foreign-agent", "1.0.0");
    }

    private AgentAppService service(AgentDomainService agentDomainService) {
        return new AgentAppService(agentDomainService, Mockito.mock(AgentWorkspaceDomainService.class),
                Mockito.mock(ScheduledTaskExecutionService.class), Mockito.mock(UserToolDomainService.class),
                Mockito.mock(UserRagDomainService.class), Mockito.mock(RagVersionDomainService.class),
                Mockito.mock(BillingService.class));
    }
}
