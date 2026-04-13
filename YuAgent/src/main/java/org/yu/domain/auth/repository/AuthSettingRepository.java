package org.yu.domain.auth.repository;

import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.auth.model.AuthSettingEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** 认证配置Repository接口 */
@Mapper
public interface AuthSettingRepository extends MyBatisPlusExtRepository<AuthSettingEntity> {
}