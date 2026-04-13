package org.yu.domain.user.repository;

import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.user.model.AccountEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** 账户仓储接口 */
@Mapper
public interface AccountRepository extends MyBatisPlusExtRepository<AccountEntity> {
}