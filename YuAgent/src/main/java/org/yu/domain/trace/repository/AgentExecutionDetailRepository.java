package org.yu.domain.trace.repository;

import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.trace.model.AgentExecutionDetailEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** Agent执行链路详细记录仓库接口 */
@Mapper
public interface AgentExecutionDetailRepository extends MyBatisPlusExtRepository<AgentExecutionDetailEntity> {
}