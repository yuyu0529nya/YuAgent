package org.yu.domain.conversation.service;

import org.springframework.stereotype.Service;
import org.yu.domain.conversation.model.SessionEntity;
import org.yu.domain.conversation.repository.SessionRepository;
import org.yu.infrastructure.exception.BusinessException;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import java.util.Collection;
import java.util.List;

@Service
public class SessionDomainService {

    private final SessionRepository sessionRepository;

    public SessionDomainService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /** 根据 agentId 获取会话列表
     * 
     * @param agentId 助理id */
    public List<SessionEntity> getSessionsByAgentId(String agentId, String userId) {
        return sessionRepository.selectList(Wrappers.<SessionEntity>lambdaQuery().eq(SessionEntity::getAgentId, agentId)
                .eq(SessionEntity::getUserId, userId).orderByDesc(SessionEntity::getCreatedAt));
    }

    /** 获取用户的全部会话，按最近更新时间倒序。 */
    public List<SessionEntity> getSessionsByUserId(String userId) {
        return sessionRepository.selectList(Wrappers.<SessionEntity>lambdaQuery().eq(SessionEntity::getUserId, userId)
                .orderByDesc(SessionEntity::getUpdatedAt));
    }

    /** 批量获取当前用户可见的会话，供统计列表避免逐条查询。 */
    public List<SessionEntity> getSessionsByIds(Collection<String> sessionIds, String userId) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return List.of();
        }
        return sessionRepository.selectList(Wrappers.<SessionEntity>lambdaQuery().in(SessionEntity::getId, sessionIds)
                .eq(SessionEntity::getUserId, userId));
    }

    /** 删除会话
     * 
     * @param sessionId 会话id
     * @param userId 用户id */
    public void deleteSession(String sessionId, String userId) {
        sessionRepository.checkedDelete(Wrappers.<SessionEntity>lambdaQuery().eq(SessionEntity::getId, sessionId)
                .eq(SessionEntity::getUserId, userId));
    }

    /** 更新会话
     * 
     * @param sessionId 会话id
     * @param userId 用户id
     * @param title 标题 */
    public void updateSession(String sessionId, String userId, String title) {
        SessionEntity session = new SessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setTitle(title);
        sessionRepository.checkedUpdate(session, Wrappers.<SessionEntity>lambdaUpdate()
                .eq(SessionEntity::getId, sessionId).eq(SessionEntity::getUserId, userId));
    }

    /** 创建会话
     * 
     * @param agentId 助理id
     * @param userId 用户id */
    public SessionEntity createSession(String agentId, String userId) {
        SessionEntity session = new SessionEntity();
        session.setAgentId(agentId);
        session.setUserId(userId);
        session.setTitle("新会话");
        sessionRepository.insert(session);
        return session;
    }

    /** 检查会话是否存在
     * 
     * @param sessionId 会话id
     * @param userId 用户id */
    public void checkSessionExist(String sessionId, String userId) {
        SessionEntity session = sessionRepository.selectOne(Wrappers.<SessionEntity>lambdaQuery()
                .eq(SessionEntity::getId, sessionId).eq(SessionEntity::getUserId, userId));
        if (session == null) {
            throw new BusinessException("会话不存在");
        }
    }

    public SessionEntity find(String sessionId, String userId) {
        return sessionRepository.selectOne(Wrappers.<SessionEntity>lambdaQuery().eq(SessionEntity::getId, sessionId)
                .eq(SessionEntity::getUserId, userId));
    }

    public void deleteSessions(List<String> sessionIds) {
        sessionRepository.delete(Wrappers.<SessionEntity>lambdaQuery().in(SessionEntity::getId, sessionIds));
    }

    public SessionEntity getSession(String sessionId, String userId) {
        SessionEntity session = sessionRepository.selectOne(Wrappers.<SessionEntity>lambdaQuery()
                .eq(SessionEntity::getId, sessionId).eq(SessionEntity::getUserId, userId));
        if (session == null) {
            throw new BusinessException("会话不存在");
        }
        return session;
    }

}
