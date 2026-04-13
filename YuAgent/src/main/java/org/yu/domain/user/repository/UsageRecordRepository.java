package org.yu.domain.user.repository;

import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.user.model.UsageRecordEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** 用量记录仓储接口 */
@Mapper
public interface UsageRecordRepository extends MyBatisPlusExtRepository<UsageRecordEntity> {
}