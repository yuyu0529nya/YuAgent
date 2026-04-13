package org.yu.domain.trace.repository;

import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.trace.model.AgentExecutionSummaryEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** Agent执行链路汇总仓库接口 */
@Mapper
public interface AgentExecutionSummaryRepository extends MyBatisPlusExtRepository<AgentExecutionSummaryEntity> {
}