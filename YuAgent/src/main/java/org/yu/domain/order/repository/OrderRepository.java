package org.yu.domain.order.repository;

import org.apache.ibatis.annotations.Mapper;
import org.yu.domain.order.model.OrderEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** 订单仓储接口 */
@Mapper
public interface OrderRepository extends MyBatisPlusExtRepository<OrderEntity> {
}