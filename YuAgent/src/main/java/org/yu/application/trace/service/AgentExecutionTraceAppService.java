package org.yu.application.trace.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.yu.application.trace.assembler.AgentExecutionTraceAssembler;
import org.yu.application.trace.dto.AgentExecutionDetailDTO;
import org.yu.application.trace.dto.AgentExecutionSummaryDTO;
import org.yu.application.trace.dto.AgentTraceListRequest;
import org.yu.application.trace.dto.AgentTraceStatisticsDTO;
import org.yu.application.trace.dto.ExecutionStatisticsDTO;
import org.yu.application.trace.dto.ExecutionTraceDTO;
import org.yu.application.trace.dto.QueryExecutionHistoryRequest;
import org.yu.application.trace.dto.SessionTraceListRequest;
import org.yu.application.trace.dto.SessionTraceStatisticsDTO;
import org.yu.application.trace.dto.TraceDetailResponse;
import org.yu.domain.agent.model.AgentEntity;
import org.yu.domain.agent.model.AgentVersionEntity;
import org.yu.domain.agent.service.AgentDomainService;
import org.yu.domain.conversation.model.SessionEntity;
import org.yu.domain.conversation.service.SessionDomainService;
import org.yu.domain.trace.model.AgentExecutionDetailEntity;
import org.yu.domain.trace.model.AgentExecutionSummaryEntity;
import org.yu.domain.trace.service.AgentExecutionTraceDomainService;
import org.yu.infrastructure.exception.BusinessException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AgentExecutionTraceAppService {

    private static final Logger logger = LoggerFactory.getLogger(AgentExecutionTraceAppService.class);
    private static final String DELETED_AGENT_NAME = "已删除助理";
    private static final String UNKNOWN_SESSION_TITLE = "未知会话";

    private final AgentExecutionTraceDomainService traceDomainService;
    private final AgentDomainService agentDomainService;
    private final SessionDomainService sessionDomainService;

    public AgentExecutionTraceAppService(AgentExecutionTraceDomainService traceDomainService,
            AgentDomainService agentDomainService, SessionDomainService sessionDomainService) {
        this.traceDomainService = traceDomainService;
        this.agentDomainService = agentDomainService;
        this.sessionDomainService = sessionDomainService;
    }

    public ExecutionTraceDTO getExecutionTrace(String traceId, String userId) {
        AgentExecutionSummaryEntity summary = traceDomainService.getExecutionSummary(traceId, userId);
        List<AgentExecutionDetailEntity> details = traceDomainService.getExecutionDetails(traceId, userId);
        return AgentExecutionTraceAssembler.toExecutionTraceDTO(summary, details);
    }

    public Page<AgentExecutionSummaryDTO> getUserExecutionHistory(String userId, QueryExecutionHistoryRequest request) {
        Page<AgentExecutionSummaryEntity> entityPage = traceDomainService.getUserExecutionHistory(userId,
                request.getPage() != null ? request.getPage() : 1,
                request.getPageSize() != null ? request.getPageSize() : 15);

        Page<AgentExecutionSummaryDTO> dtoPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(),
                entityPage.getTotal());
        dtoPage.setRecords(AgentExecutionTraceAssembler.toSummaryDTOs(entityPage.getRecords()));
        return dtoPage;
    }

    public List<AgentExecutionSummaryDTO> getSessionExecutionHistory(String sessionId, String userId) {
        List<AgentExecutionSummaryEntity> entities = traceDomainService.getSessionExecutionHistory(sessionId, userId);
        return AgentExecutionTraceAssembler.toSummaryDTOs(entities);
    }

    public List<AgentExecutionSummaryDTO> getUserExecutionsByTimeRange(String userId, LocalDateTime startTime,
            LocalDateTime endTime) {
        List<AgentExecutionSummaryEntity> entities = traceDomainService.getUserExecutionsByTimeRange(userId, startTime,
                endTime);
        return AgentExecutionTraceAssembler.toSummaryDTOs(entities);
    }

    public List<AgentExecutionSummaryDTO> getUserFailedExecutions(String userId) {
        List<AgentExecutionSummaryEntity> entities = traceDomainService.getUserFailedExecutions(userId);
        return AgentExecutionTraceAssembler.toSummaryDTOs(entities);
    }

    public ExecutionStatisticsDTO getUserExecutionStatistics(String userId) {
        AgentExecutionTraceDomainService.ExecutionStatistics statistics = traceDomainService
                .getUserExecutionStatistics(userId);
        return AgentExecutionTraceAssembler.toStatisticsDTO(statistics);
    }

    public List<AgentExecutionDetailDTO> getToolCallsByTraceId(String traceId, String userId) {
        traceDomainService.getExecutionSummary(traceId, userId);
        List<AgentExecutionDetailEntity> entities = traceDomainService.getToolCallsBySessionId(traceId);
        return AgentExecutionTraceAssembler.toDetailDTOs(entities);
    }

    public List<AgentExecutionDetailDTO> getModelCallsByTraceId(String traceId, String userId) {
        traceDomainService.getExecutionSummary(traceId, userId);
        List<AgentExecutionDetailEntity> entities = traceDomainService.getModelCallsBySessionId(traceId);
        return AgentExecutionTraceAssembler.toDetailDTOs(entities);
    }

    public List<AgentExecutionDetailDTO> getFallbackCallsByTraceId(String traceId, String userId) {
        traceDomainService.getExecutionSummary(traceId, userId);
        List<AgentExecutionDetailEntity> entities = traceDomainService.getFallbackCallsBySessionId(traceId);
        return AgentExecutionTraceAssembler.toDetailDTOs(entities);
    }

    public Page<AgentExecutionSummaryDTO> getExecutionHistory(QueryExecutionHistoryRequest request, String userId) {
        return getUserExecutionHistory(userId, request);
    }

    public TraceDetailResponse getTraceDetail(String traceId, String userId) {
        AgentExecutionSummaryEntity summary = traceDomainService.getExecutionSummary(traceId, userId);
        List<AgentExecutionDetailEntity> details = traceDomainService.getExecutionDetails(traceId, userId);
        AgentExecutionSummaryDTO summaryDTO = AgentExecutionTraceAssembler.toSummaryDTO(summary);
        List<AgentExecutionDetailDTO> detailDTOs = AgentExecutionTraceAssembler.toDetailDTOs(details);
        return new TraceDetailResponse(summaryDTO, detailDTOs);
    }

    public List<AgentExecutionDetailDTO> getExecutionDetails(String traceId, String userId) {
        List<AgentExecutionDetailEntity> entities = traceDomainService.getExecutionDetails(traceId, userId);
        return AgentExecutionTraceAssembler.toDetailDTOs(entities);
    }

    public List<AgentExecutionSummaryDTO> getSessionExecutionHistoryByUserId(String sessionId, String userId) {
        List<AgentExecutionSummaryEntity> entities = traceDomainService.getSessionExecutionHistory(sessionId, userId);
        List<AgentExecutionSummaryDTO> dtoList = AgentExecutionTraceAssembler.toSummaryDTOs(entities);

        for (AgentExecutionSummaryDTO dto : dtoList) {
            if (dto.getAgentId() != null) {
                dto.setAgentName(getAgentName(dto.getAgentId(), userId));
            }
        }

        return dtoList;
    }

    public List<AgentTraceStatisticsDTO> getUserAgentTraceStatistics(AgentTraceListRequest request, String userId) {
        List<AgentExecutionTraceDomainService.AgentStatistics> agentStatistics = traceDomainService
                .getUserAgentStatistics(userId);

        if (agentStatistics.isEmpty()) {
            return List.of();
        }

        List<String> agentIds = agentStatistics.stream()
                .map(AgentExecutionTraceDomainService.AgentStatistics::getAgentId)
                .collect(Collectors.toList());
        Map<String, String> agentNameMap = getAgentNameMap(agentIds, userId);

        return agentStatistics.stream().map(stats -> {
            AgentTraceStatisticsDTO dto = new AgentTraceStatisticsDTO();
            dto.setAgentId(stats.getAgentId());
            dto.setAgentName(agentNameMap.getOrDefault(stats.getAgentId(), DELETED_AGENT_NAME));
            dto.setTotalExecutions(stats.getTotalExecutions());
            dto.setSuccessfulExecutions(stats.getSuccessfulExecutions());
            dto.setFailedExecutions(stats.getFailedExecutions());
            dto.setSuccessRate(stats.getSuccessRate());
            dto.setTotalTokens(stats.getTotalTokens());
            dto.setTotalInputTokens(stats.getTotalInputTokens());
            dto.setTotalOutputTokens(stats.getTotalOutputTokens());
            dto.setTotalToolCalls(stats.getTotalToolCalls());
            dto.setTotalSessions(stats.getTotalSessions());
            dto.setLastExecutionTime(stats.getLastExecutionTime());
            dto.setLastExecutionSuccess(stats.getLastExecutionSuccess());
            return dto;
        }).collect(Collectors.toList());
    }

    public List<SessionTraceStatisticsDTO> getAgentSessionTraceStatistics(String agentId,
            SessionTraceListRequest request, String userId) {
        List<AgentExecutionTraceDomainService.SessionStatistics> sessionStatistics = traceDomainService
                .getAgentSessionStatistics(agentId, userId);

        if (sessionStatistics.isEmpty()) {
            return List.of();
        }

        String agentName = getAgentName(agentId, userId);
        List<String> sessionIds = sessionStatistics.stream()
                .map(AgentExecutionTraceDomainService.SessionStatistics::getSessionId)
                .collect(Collectors.toList());
        Map<String, SessionEntity> sessionMap = getSessionMap(sessionIds, userId);

        return sessionStatistics.stream().map(stats -> {
            SessionTraceStatisticsDTO dto = new SessionTraceStatisticsDTO();
            dto.setSessionId(stats.getSessionId());
            dto.setAgentId(stats.getAgentId());
            dto.setAgentName(agentName);

            SessionEntity session = sessionMap.get(stats.getSessionId());
            if (session != null) {
                dto.setSessionTitle(session.getTitle());
                dto.setSessionCreatedTime(session.getCreatedAt());
                dto.setIsArchived(session.isArchived());
            } else {
                dto.setSessionTitle(UNKNOWN_SESSION_TITLE);
                dto.setIsArchived(false);
            }

            dto.setTotalExecutions(stats.getTotalExecutions());
            dto.setSuccessfulExecutions(stats.getSuccessfulExecutions());
            dto.setFailedExecutions(stats.getFailedExecutions());
            dto.setSuccessRate(stats.getSuccessRate());
            dto.setTotalTokens(stats.getTotalTokens());
            dto.setTotalInputTokens(stats.getTotalInputTokens());
            dto.setTotalOutputTokens(stats.getTotalOutputTokens());
            dto.setTotalToolCalls(stats.getTotalToolCalls());
            dto.setTotalExecutionTime(stats.getTotalExecutionTime());
            dto.setLastExecutionTime(stats.getLastExecutionTime());
            dto.setLastExecutionSuccess(stats.getLastExecutionSuccess());
            return dto;
        }).collect(Collectors.toList());
    }

    public void deleteAgentTraceRecords(String agentId, String userId) {
        int deleted = traceDomainService.deleteAgentTraceRecords(agentId, userId);
        if (deleted == 0) {
            throw new BusinessException("未找到可删除的执行追踪记录");
        }
    }

    private Map<String, String> getAgentNameMap(List<String> agentIds, String userId) {
        return agentIds.stream().distinct()
                .collect(Collectors.toMap(Function.identity(), agentId -> getAgentName(agentId, userId)));
    }

    private String getAgentName(String agentId, String userId) {
        if (agentId == null || agentId.isBlank()) {
            return DELETED_AGENT_NAME;
        }

        try {
            AgentEntity agent = agentDomainService.getAgent(agentId, userId);
            logger.debug("Loaded private agent {} for user {}", agentId, userId);
            return agent != null ? agent.getName() : DELETED_AGENT_NAME;
        } catch (Throwable e) {
            logger.debug("Unable to load agent {} directly, trying fallback lookup: {}", agentId, e.getMessage());

            try {
                AgentVersionEntity publishedVersion = agentDomainService.getPublishedAgentVersion(agentId);
                if (publishedVersion != null) {
                    return publishedVersion.getName();
                }
            } catch (Throwable ex) {
                logger.debug("Published version lookup failed for agent {}: {}", agentId, ex.getMessage());
            }

            try {
                AgentVersionEntity versionEntity = agentDomainService.getAgentVersionById(agentId);
                if (versionEntity != null) {
                    return versionEntity.getName();
                }
            } catch (Throwable ex) {
                logger.debug("Version id lookup failed for agent {}: {}", agentId, ex.getMessage());
            }

            logger.warn("Unable to resolve agent name for historical trace, agentId={}", agentId);
            return DELETED_AGENT_NAME;
        }
    }

    private Map<String, SessionEntity> getSessionMap(List<String> sessionIds, String userId) {
        return sessionIds.stream().distinct()
                .map(sessionId -> {
                    SessionEntity session = getSession(sessionId, userId);
                    if (session == null) {
                        return null;
                    }
                    return Map.entry(sessionId, session);
                })
                .filter(entry -> entry != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private SessionEntity getSession(String sessionId, String userId) {
        try {
            return sessionDomainService.getSession(sessionId, userId);
        } catch (Exception e) {
            return null;
        }
    }
}
