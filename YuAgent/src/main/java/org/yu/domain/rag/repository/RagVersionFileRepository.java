package org.yu.domain.rag.repository;

import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.rag.model.RagVersionFileEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** RAG版本文件仓储接口
 * @author yu
 * @date 2025-07-16 <br/>
 */
@Mapper
public interface RagVersionFileRepository extends MyBatisPlusExtRepository<RagVersionFileEntity> {

}