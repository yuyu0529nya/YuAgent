package org.yu.domain.conversation.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.conversation.model.ContextEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** 上下文仓库接口 */
@Mapper
public interface ContextRepository extends MyBatisPlusExtRepository<ContextEntity> {
}