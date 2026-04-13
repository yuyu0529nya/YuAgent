package org.yu.domain.rag.repository;

import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.rag.model.FileDetailEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** 文件详情仓库接口
 * @author zang */
@Mapper
public interface FileDetailRepository extends MyBatisPlusExtRepository<FileDetailEntity> {

}