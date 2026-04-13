package org.yu.domain.scheduledtask.repository;

import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.scheduledtask.model.ScheduledTaskEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** 定时任务仓储接口 */
@Mapper
public interface ScheduledTaskRepository extends MyBatisPlusExtRepository<ScheduledTaskEntity> {
}