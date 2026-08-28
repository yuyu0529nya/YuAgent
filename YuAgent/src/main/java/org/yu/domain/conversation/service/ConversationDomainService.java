package org.yu.domain.conversation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.yu.domain.conversation.constant.Role;
import org.yu.domain.conversation.model.MessageEntity;
import org.yu.domain.conversation.repository.MessageRepository;

import java.util.List;

/** 对话服务实现 */
@Service
public class ConversationDomainService {

    private final MessageRepository messageRepository;

    public ConversationDomainService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    /** 获取会话中的消息列表
     *
     * @param sessionId 会话id
     * @return 消息列表 */
    public List<MessageEntity> getConversationMessages(String sessionId) {
        return messageRepository
                .selectList(Wrappers.<MessageEntity>lambdaQuery().eq(MessageEntity::getSessionId, sessionId)
                        .ne(MessageEntity::getRole, Role.SUMMARY).orderByAsc(MessageEntity::getCreatedAt));
    }

    public MessageEntity saveMessage(MessageEntity message) {
        messageRepository.insert(message);
        return message;
    }

    /** 删除会话下的消息
     * 
     * @param sessionId 会话id */
    public void deleteConversationMessages(String sessionId) {
        messageRepository.delete(Wrappers.<MessageEntity>lambdaQuery().eq(MessageEntity::getSessionId, sessionId));
    }

    public void deleteConversationMessages(List<String> sessionIds) {
        messageRepository
                .checkedDelete(Wrappers.<MessageEntity>lambdaQuery().in(MessageEntity::getSessionId, sessionIds));
    }

}
