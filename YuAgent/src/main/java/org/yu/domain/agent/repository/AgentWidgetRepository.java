package org.yu.domain.agent.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.yu.domain.agent.model.AgentWidgetEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

import java.util.List;

/** Agent小组件配置仓储接口 */
@Mapper
public interface AgentWidgetRepository extends MyBatisPlusExtRepository<AgentWidgetEntity> {

}