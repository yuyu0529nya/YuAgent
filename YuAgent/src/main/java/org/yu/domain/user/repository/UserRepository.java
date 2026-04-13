package org.yu.domain.user.repository;

import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.user.model.UserEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** 模型仓储接口 */
@Mapper
public interface UserRepository extends MyBatisPlusExtRepository<UserEntity> {

}