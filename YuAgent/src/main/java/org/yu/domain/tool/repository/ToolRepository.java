package org.yu.domain.tool.repository;

import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.tool.model.ToolEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** 工具仓储接口 */
@Mapper
public interface ToolRepository extends MyBatisPlusExtRepository<ToolEntity> {
}