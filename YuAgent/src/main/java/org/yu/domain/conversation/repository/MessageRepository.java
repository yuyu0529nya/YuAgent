package org.yu.domain.conversation.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.conversation.model.MessageEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** 消息仓库接口 */
@Mapper
public interface MessageRepository extends MyBatisPlusExtRepository<MessageEntity> {
}