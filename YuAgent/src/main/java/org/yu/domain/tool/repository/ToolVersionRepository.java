package org.yu.domain.tool.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.yu.domain.tool.model.ToolVersionEntity;
import org.yu.infrastructure.repository.MyBatisPlusExtRepository;

@Mapper
public interface ToolVersionRepository extends MyBatisPlusExtRepository<ToolVersionEntity> {

    @Select("SELECT * FROM (" + "SELECT t.*, ROW_NUMBER() OVER (PARTITION BY t.tool_id "
            + "ORDER BY t.created_at DESC, t.id DESC) AS row_number "
            + "FROM tool_versions t WHERE t.public_status = true AND t.deleted_at IS NULL" + ") latest "
            + "WHERE latest.row_number = 1 "
            + "AND (#{toolName} IS NULL OR #{toolName} = '' OR latest.name LIKE CONCAT('%', #{toolName}, '%')) "
            + "ORDER BY latest.created_at DESC, latest.id DESC")
    Page<ToolVersionEntity> selectLatestPublicToolVersions(Page<ToolVersionEntity> page,
            @Param("toolName") String toolName);
}
