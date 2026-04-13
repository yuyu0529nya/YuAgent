package org.yu.domain.rag.repository;

import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.rag.model.UserRagFileEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** 用户RAG文件快照仓储接口
 * @author yu
 * @date 2025-07-22 <br/>
 */
@Mapper
public interface UserRagFileRepository extends MyBatisPlusExtRepository<UserRagFileEntity> {

}