package org.yu.domain.agent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.agent.model.AgentEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** Agent仓库接口 */
@Mapper
public interface AgentRepository extends MyBatisPlusExtRepository<AgentEntity> {
}