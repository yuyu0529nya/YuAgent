package org.yu.domain.rag.repository;

import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.rag.model.FileDetailEntity;
import org.yu.domain.rag.model.RagQaDatasetEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** @author shilong.zang
 * @date 17:44 <br/>
 */
@Mapper
public interface RagQaDatasetRepository extends MyBatisPlusExtRepository<RagQaDatasetEntity> {

}
