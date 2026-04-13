package org.yu.domain.conversation.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.conversation.model.SessionEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** 会话仓库接口 */
@Mapper
public interface SessionRepository extends MyBatisPlusExtRepository<SessionEntity> {
}