package org.yu.domain.product.repository;

import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.product.model.ProductEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** 商品仓储接口 */
@Mapper
public interface ProductRepository extends MyBatisPlusExtRepository<ProductEntity> {
}