package org.yu.domain.rag.repository;

import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.rag.model.DocumentUnitEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** @author shilong.zang
 * @date 21:07 <br/>
 */
@Mapper
public interface DocumentUnitRepository extends MyBatisPlusExtRepository<DocumentUnitEntity> {

}
