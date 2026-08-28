package org.yu.application.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yu.application.agent.dto.AgentDTO;
import org.yu.application.conversation.dto.SessionDTO;
import org.yu.application.conversation.service.ChatSessionManager;
import org.yu.domain.agent.model.AgentEntity;
import org.yu.domain.agent.service.AgentDomainService;
import org.yu.domain.agent.service.AgentWorkspaceDomainService;
import org.yu.domain.conversation.model.SessionEntity;
import org.yu.domain.conversation.service.ConversationDomainService;
import org.yu.domain.conversation.service.SessionDomainService;
import org.yu.domain.scheduledtask.service.ScheduledTaskExecutionService;
import org.yu.infrastructure.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class AgentSessionAppServiceTest {

    @Mock
    private AgentWorkspaceDomainService agentWorkspaceDomainService;
    @Mock
    private AgentDomainService agentDomainService;
    @Mock
    private SessionDomainService sessionDomainService;
    @Mock
    private ConversationDomainService conversationDomainService;
    @Mock
    private ScheduledTaskExecutionService scheduledTaskExecutionService;
    @Mock
    private ChatSessionManager chatSessionManager;

    @InjectMocks
    private AgentSessionAppService agentSessionAppService;

    @Test
    void shouldReturnAgentForOwnedSession() {
        SessionEntity session = new SessionEntity();
        session.setAgentId("agent-1");
        AgentEntity agent = new AgentEntity();
        agent.setId("agent-1");
        agent.setName("旅行助手");

        when(sessionDomainService.getSession("session-1", "user-1")).thenReturn(session);
        when(agentDomainService.getAgentById("agent-1")).thenReturn(agent);

        AgentDTO result = agentSessionAppService.getAgentBySessionId("session-1", "user-1");

        assertEquals("agent-1", result.getId());
        assertEquals("旅行助手", result.getName());
        verify(sessionDomainService).getSession("session-1", "user-1");
        verify(agentDomainService).getAgentById("agent-1");
    }

    @Test
    void shouldCheckPermissionBeforeCreatingASession() {
        when(agentDomainService.getAgentWithPermissionCheck("foreign-agent", "user-1"))
                .thenThrow(new BusinessException("助理不存在"));

        assertThrows(BusinessException.class,
                () -> agentSessionAppService.createSession("user-1", "foreign-agent"));

        verify(agentDomainService).getAgentWithPermissionCheck("foreign-agent", "user-1");
        verify(sessionDomainService, never()).createSession("foreign-agent", "user-1");
        verify(conversationDomainService, never()).saveMessage(any());
    }

    @Test
    void shouldReturnOnlyTheCurrentUsersSessions() {
        SessionEntity session = new SessionEntity();
        session.setId("session-1");
        session.setUserId("user-1");
        session.setTitle("最近会话");
        when(sessionDomainService.getSessionsByUserId("user-1")).thenReturn(java.util.List.of(session));

        java.util.List<SessionDTO> result = agentSessionAppService.getUserSessionList("user-1");

        assertEquals(1, result.size());
        assertEquals("session-1", result.get(0).getId());
        verify(sessionDomainService).getSessionsByUserId("user-1");
    }

    @Test
    void shouldCheckSessionOwnershipBeforeInterrupting() {
        when(chatSessionManager.interruptSession("session-1")).thenReturn(true);

        boolean interrupted = agentSessionAppService.interruptSession("session-1", "user-1");

        assertEquals(true, interrupted);
        verify(sessionDomainService).checkSessionExist("session-1", "user-1");
        verify(chatSessionManager).interruptSession("session-1");
    }
}
