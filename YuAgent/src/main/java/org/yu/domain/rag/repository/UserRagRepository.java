package org.yu.domain.rag.repository;

import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.rag.model.UserRagEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** 用户安装的RAG仓储接口
 * @author yu
 * @date 2025-07-16 <br/>
 */
@Mapper
public interface UserRagRepository extends MyBatisPlusExtRepository<UserRagEntity> {

}