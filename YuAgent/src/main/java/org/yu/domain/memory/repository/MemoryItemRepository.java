package org.yu.domain.memory.repository;

import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.memory.model.MemoryItemEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** memory_items 表数据访问（基于 MyBatis-Plus 提供通用 CRUD） */
@Mapper
public interface MemoryItemRepository extends MyBatisPlusExtRepository<MemoryItemEntity> {
}
