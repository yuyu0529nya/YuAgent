package org.yu.domain.rag.repository;

import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.rag.model.RagVersionEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** RAG版本仓储接口
 * @author yu
 * @date 2025-07-16 <br/>
 */
@Mapper
public interface RagVersionRepository extends MyBatisPlusExtRepository<RagVersionEntity> {

}