package org.yu.domain.conversation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.yu.domain.conversation.model.ContextEntity;
import org.yu.domain.conversation.repository.ContextRepository;
import org.yu.infrastructure.exception.BusinessException;

@Service
public class ContextDomainService {

    private static final Logger logger = LoggerFactory.getLogger(ContextDomainService.class);

    private final ContextRepository contextRepository;

    public ContextDomainService(ContextRepository contextRepository) {
        this.contextRepository = contextRepository;
    }

    // 获取历史消息id
    public ContextEntity getBySessionId(String sessionId) {
        LambdaQueryWrapper<ContextEntity> wrapper = Wrappers.<ContextEntity>lambdaQuery()
                .eq(ContextEntity::getSessionId, sessionId).select();
        ContextEntity contextEntity = contextRepository.selectOne(wrapper);
        if (contextEntity == null) {
            throw new BusinessException("消息上下文不存在");
        }
        return contextEntity;
    }

    public ContextEntity findBySessionId(String sessionId) {
        LambdaQueryWrapper<ContextEntity> wrapper = Wrappers.<ContextEntity>lambdaQuery()
                .eq(ContextEntity::getSessionId, sessionId);
        return contextRepository.selectOne(wrapper);
    }

    public ContextEntity insertOrUpdate(ContextEntity contextEntity) {
        validateContext(contextEntity);
        try {
            if (contextEntity.getId() == null || contextEntity.getId().isBlank()) {
                contextEntity.setId(UUID.randomUUID().toString().replace("-", ""));
                contextRepository.insert(contextEntity);
            } else {
                contextRepository.checkedUpdateById(contextEntity);
            }
        } catch (DuplicateKeyException e) {
            updateExistingContext(contextEntity, e);
        } catch (Exception e) {
            logger.error("保存消息上下文失败: sessionId={}", contextEntity.getSessionId(), e);
            throw new BusinessException("保存消息上下文失败", e);
        }
        return contextEntity;
    }

    private void validateContext(ContextEntity contextEntity) {
        if (contextEntity == null || contextEntity.getSessionId() == null || contextEntity.getSessionId().isBlank()) {
            throw new BusinessException("会话ID不能为空");
        }
    }

    private void updateExistingContext(ContextEntity contextEntity, DuplicateKeyException cause) {
        ContextEntity existingContext = findBySessionId(contextEntity.getSessionId());
        if (existingContext == null) {
            logger.error("上下文唯一约束冲突后未找到已有记录: sessionId={}", contextEntity.getSessionId(), cause);
            throw new BusinessException("保存消息上下文失败", cause);
        }
        contextEntity.setId(existingContext.getId());
        try {
            contextRepository.checkedUpdateById(contextEntity);
        } catch (Exception e) {
            logger.error("更新并发创建的消息上下文失败: sessionId={}", contextEntity.getSessionId(), e);
            throw new BusinessException("保存消息上下文失败", e);
        }
    }
}
