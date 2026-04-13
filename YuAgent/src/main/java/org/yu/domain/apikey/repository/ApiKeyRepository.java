package org.yu.domain.apikey.repository;

import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.apikey.model.ApiKeyEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** API密钥仓储接口 */
@Mapper
public interface ApiKeyRepository extends MyBatisPlusExtRepository<ApiKeyEntity> {
}