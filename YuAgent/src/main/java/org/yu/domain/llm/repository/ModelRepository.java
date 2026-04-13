package org.yu.domain.llm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.llm.model.ModelEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** 模型仓储接口 */
@Mapper
public interface ModelRepository extends MyBatisPlusExtRepository<ModelEntity> {

}