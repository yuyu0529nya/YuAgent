package org.yu.domain.user.repository;

import java.math.BigDecimal;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.yu.domain.user.model.UsageRecordEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

/** 用量记录仓储接口 */
@Mapper
public interface UsageRecordRepository extends MyBatisPlusExtRepository<UsageRecordEntity> {

    @Select("SELECT COALESCE(SUM(cost), 0) FROM usage_records WHERE user_id = #{userId} AND deleted_at IS NULL")
    BigDecimal sumCostByUserId(@Param("userId") String userId);
}
