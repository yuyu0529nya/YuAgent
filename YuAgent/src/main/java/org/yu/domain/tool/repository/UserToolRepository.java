package org.yu.domain.tool.repository;

import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.tool.model.ToolVersionEntity;
import org.yu.domain.tool.model.UserToolEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

@Mapper
public interface UserToolRepository extends MyBatisPlusExtRepository<UserToolEntity> {
}
