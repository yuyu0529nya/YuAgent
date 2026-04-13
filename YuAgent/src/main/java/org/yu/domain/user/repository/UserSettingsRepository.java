package org.yu.domain.user.repository;

import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.user.model.UserSettingsEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** 用户设置仓储接口 */
@Mapper
public interface UserSettingsRepository extends MyBatisPlusExtRepository<UserSettingsEntity> {

}