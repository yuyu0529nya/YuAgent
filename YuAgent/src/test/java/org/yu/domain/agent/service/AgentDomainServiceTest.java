package org.yu.domain.agent.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.yu.domain.agent.model.AgentEntity;
import org.yu.domain.agent.repository.AgentRepository;
import org.yu.domain.agent.repository.AgentVersionRepository;
import org.yu.domain.agent.repository.AgentWorkspaceRepository;
import org.yu.domain.user.repository.UserRepository;
import org.yu.infrastructure.exception.BusinessException;

class AgentDomainServiceTest {

    @Test
    void shouldReturnAgentFromPrimaryKeyLookup() {
        AgentRepository agentRepository = Mockito.mock(AgentRepository.class);
        AgentEntity agent = new AgentEntity();
        when(agentRepository.selectById("agent-1")).thenReturn(agent);

        AgentDomainService service = service(agentRepository);

        assertSame(agent, service.getAgentById("agent-1"));
        verify(agentRepository).selectById("agent-1");
    }

    @Test
    void shouldReportMissingAgentInsteadOfAccessingAnEmptyList() {
        AgentRepository agentRepository = Mockito.mock(AgentRepository.class);
        when(agentRepository.selectById("missing-agent")).thenReturn(null);

        assertThrows(BusinessException.class, () -> service(agentRepository).getAgentById("missing-agent"));
    }

    @Test
    void shouldOnlyToggleStatusForTheOwner() {
        AgentRepository agentRepository = Mockito.mock(AgentRepository.class);
        AgentEntity agent = new AgentEntity();
        agent.setEnabled(true);
        when(agentRepository.selectOne(any())).thenReturn(agent);

        AgentEntity result = service(agentRepository).toggleAgentStatus("agent-1", "user-1");

        assertSame(agent, result);
        assertFalse(result.getEnabled());
        verify(agentRepository).selectOne(any());
        verify(agentRepository).checkedUpdateById(agent);
    }

    @Test
    void shouldRejectStatusToggleWhenTheAgentIsNotOwned() {
        AgentRepository agentRepository = Mockito.mock(AgentRepository.class);
        when(agentRepository.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> service(agentRepository).toggleAgentStatus("foreign-agent", "user-1"));
        verify(agentRepository).selectOne(any());
    }

    private AgentDomainService service(AgentRepository agentRepository) {
        return new AgentDomainService(agentRepository, Mockito.mock(AgentVersionRepository.class),
                Mockito.mock(AgentWorkspaceRepository.class), Mockito.mock(UserRepository.class));
    }
}
