package org.yu.application.trace.service;

import org.junit.jupiter.api.Test;
import org.yu.application.trace.dto.AgentTraceListRequest;
import org.yu.application.trace.dto.AgentTraceStatisticsDTO;
import org.yu.application.trace.dto.SessionTraceListRequest;
import org.yu.application.trace.dto.SessionTraceStatisticsDTO;
import org.yu.domain.agent.service.AgentDomainService;
import org.yu.domain.conversation.service.SessionDomainService;
import org.yu.domain.trace.service.AgentExecutionTraceDomainService;
import org.yu.infrastructure.exception.BusinessException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentExecutionTraceAppServiceTest {

    private final AgentExecutionTraceDomainService traceDomainService = mock(AgentExecutionTraceDomainService.class);
    private final AgentDomainService agentDomainService = mock(AgentDomainService.class);
    private final SessionDomainService sessionDomainService = mock(SessionDomainService.class);

    private final AgentExecutionTraceAppService service = new AgentExecutionTraceAppService(traceDomainService,
            agentDomainService, sessionDomainService);

    @Test
    void shouldFallbackToDeletedAgentNameWhenAgentHasBeenRemoved() {
        String userId = "user-1";
        String agentId = "agent-deleted";
        AgentExecutionTraceDomainService.AgentStatistics stats = new AgentExecutionTraceDomainService.AgentStatistics(
                agentId, 3, 2, 1, 2D / 3D, 120, 60, 60, 1, 2, LocalDateTime.now(), true);

        when(traceDomainService.getUserAgentStatistics(userId)).thenReturn(List.of(stats));
        when(agentDomainService.getAgent(agentId, userId)).thenThrow(new BusinessException("Agent不存在"));
        when(agentDomainService.getPublishedAgentVersion(agentId)).thenReturn(null);
        when(agentDomainService.getAgentVersionById(agentId)).thenReturn(null);

        List<AgentTraceStatisticsDTO> result = service.getUserAgentTraceStatistics(new AgentTraceListRequest(), userId);

        assertEquals(1, result.size());
        assertEquals("已删除助理", result.get(0).getAgentName());
        assertEquals(agentId, result.get(0).getAgentId());
    }

    @Test
    void shouldReturnDeletedAgentNameForDeletedAgentSessionStatistics() {
        String userId = "user-1";
        String agentId = "agent-deleted";
        AgentExecutionTraceDomainService.SessionStatistics stats = new AgentExecutionTraceDomainService.SessionStatistics(
                "session-1", agentId, 2, 2, 0, 1.0, 50, 25, 25, 0, 800, LocalDateTime.now(), true);

        when(traceDomainService.getAgentSessionStatistics(agentId, userId)).thenReturn(List.of(stats));
        when(agentDomainService.getAgent(agentId, userId)).thenThrow(new BusinessException("Agent不存在"));
        when(agentDomainService.getPublishedAgentVersion(agentId)).thenReturn(null);
        when(agentDomainService.getAgentVersionById(agentId)).thenReturn(null);

        List<SessionTraceStatisticsDTO> result = service.getAgentSessionTraceStatistics(agentId,
                new SessionTraceListRequest(), userId);

        assertFalse(result.isEmpty());
        assertEquals("已删除助理", result.get(0).getAgentName());
        assertEquals("未知会话", result.get(0).getSessionTitle());
        verify(sessionDomainService).getSessionsByIds(List.of("session-1"), userId);
    }

    @Test
    void shouldDeleteAgentTraceRecordsWithoutRequiringExistingAgent() {
        String userId = "user-1";
        String agentId = "agent-deleted";

        when(traceDomainService.deleteAgentTraceRecords(agentId, userId)).thenReturn(2);

        assertDoesNotThrow(() -> service.deleteAgentTraceRecords(agentId, userId));

        verify(traceDomainService).deleteAgentTraceRecords(agentId, userId);
    }
}
