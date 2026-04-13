package org.yu.domain.rule.repository;

import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.rule.model.RuleEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** 规则仓储接口 */
@Mapper
public interface RuleRepository extends MyBatisPlusExtRepository<RuleEntity> {
}