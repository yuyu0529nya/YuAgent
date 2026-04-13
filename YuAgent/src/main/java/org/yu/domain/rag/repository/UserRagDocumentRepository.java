package org.yu.domain.rag.repository;

import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.rag.model.UserRagDocumentEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** 用户RAG文档快照仓储接口
 * @author yu
 * @date 2025-07-22 <br/>
 */
@Mapper
public interface UserRagDocumentRepository extends MyBatisPlusExtRepository<UserRagDocumentEntity> {

}