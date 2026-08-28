package org.yu.domain.tool.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

import org.springframework.stereotype.Service;
import org.yu.domain.tool.model.ToolVersionEntity;
import org.yu.domain.tool.repository.ToolVersionRepository;
import org.yu.infrastructure.exception.BusinessException;
import org.yu.interfaces.dto.tool.request.QueryToolRequest;

@Service
public class ToolVersionDomainService {

    private final ToolVersionRepository toolVersionRepository;

    public ToolVersionDomainService(ToolVersionRepository toolVersionRepository) {
        this.toolVersionRepository = toolVersionRepository;
    }

    public Page<ToolVersionEntity> listToolVersion(QueryToolRequest queryToolRequest) {
        long page = Math.max(queryToolRequest.getPage() == null ? 1 : queryToolRequest.getPage(), 1);
        long pageSize = Math.max(queryToolRequest.getPageSize() == null ? 15 : queryToolRequest.getPageSize(), 1);
        return toolVersionRepository.selectLatestPublicToolVersions(new Page<>(page, pageSize),
                queryToolRequest.getToolName());
    }

    /** 获取工具版本（带权限验证）
     * 
     * @param toolId 工具ID
     * @param version 版本号
     * @param userId 当前用户ID
     * @return 工具版本实体 */
    public ToolVersionEntity getToolVersion(String toolId, String version, String userId) {
        ToolVersionEntity toolVersionEntity = getToolVersionWithoutPermissionCheck(toolId, version);

        // 权限验证：如果不是创建者，只能获取公开版本
        if (!userId.equals(toolVersionEntity.getUserId())
                && !Boolean.TRUE.equals(toolVersionEntity.getPublicStatus())) {
            throw new BusinessException("该工具版本未公开，无权访问");
        }

        return toolVersionEntity;
    }

    /** 获取工具版本（无权限验证，仅供内部使用）
     * 
     * @param toolId 工具ID
     * @param version 版本号
     * @return 工具版本实体 */
    private ToolVersionEntity getToolVersionWithoutPermissionCheck(String toolId, String version) {
        Wrapper<ToolVersionEntity> wrapper = Wrappers.<ToolVersionEntity>lambdaQuery()
                .eq(ToolVersionEntity::getToolId, toolId).eq(ToolVersionEntity::getVersion, version);
        ToolVersionEntity toolVersionEntity = toolVersionRepository.selectOne(wrapper);
        if (toolVersionEntity == null) {
            throw new BusinessException("工具版本不存在: " + toolId + " " + version);
        }
        return toolVersionEntity;
    }

    /** 获取工具版本（无权限验证，向后兼容）
     * 
     * @param toolId 工具ID
     * @param version 版本号
     * @return 工具版本实体
     * @deprecated 使用带userId参数的版本以确保权限安全 */
    @Deprecated
    public ToolVersionEntity getToolVersion(String toolId, String version) {
        return getToolVersionWithoutPermissionCheck(toolId, version);
    }

    public void addToolVersion(ToolVersionEntity toolVersionEntity) {
        toolVersionRepository.insert(toolVersionEntity);
    }

    public ToolVersionEntity findLatestToolVersion(String toolId, String userId) {

        LambdaQueryWrapper<ToolVersionEntity> queryWrapper = Wrappers.<ToolVersionEntity>lambdaQuery()
                .eq(ToolVersionEntity::getToolId, toolId).orderByDesc(ToolVersionEntity::getCreatedAt).last("LIMIT 1");

        ToolVersionEntity toolVersionEntity = toolVersionRepository.selectOne(queryWrapper);
        if (toolVersionEntity == null) {
            return null; // 第一次发布时没有版本，返回null而不是抛出异常
        }
        return toolVersionEntity;
    }

    /** 获取工具的所有版本，各根据当前用户判断，如果是当前用户则返回所有版本，如果不是则返回公开的版本
     * @param toolId 工具 id
     * @param userId 用户 id
     * @return */
    public List<ToolVersionEntity> getToolVersions(String toolId, String userId) {
        // 先查询工具的创建者是谁
        LambdaQueryWrapper<ToolVersionEntity> creatorQuery = Wrappers.<ToolVersionEntity>lambdaQuery()
                .eq(ToolVersionEntity::getToolId, toolId).orderByDesc(ToolVersionEntity::getCreatedAt).last("LIMIT 1");
        ToolVersionEntity tool = toolVersionRepository.selectOne(creatorQuery);

        // 如果工具不存在，返回空列表
        if (tool == null) {
            throw new BusinessException("工具版本不存在");
        }

        LambdaQueryWrapper<ToolVersionEntity> queryWrapper = Wrappers.<ToolVersionEntity>lambdaQuery()
                .eq(ToolVersionEntity::getToolId, toolId).orderByDesc(ToolVersionEntity::getCreatedAt);

        // 如果当前用户是创建者，返回所有版本；否则只返回公开版本
        if (!userId.equals(tool.getUserId())) {
            queryWrapper.eq(ToolVersionEntity::getPublicStatus, true);
        }

        return toolVersionRepository.selectList(queryWrapper);
    }

    public void updateToolVersionStatus(String toolId, String version, String userId, Boolean publishStatus) {
        Wrapper<ToolVersionEntity> wrapper = Wrappers.<ToolVersionEntity>lambdaUpdate()
                .eq(ToolVersionEntity::getToolId, toolId).eq(ToolVersionEntity::getVersion, version)
                .eq(ToolVersionEntity::getUserId, userId).set(ToolVersionEntity::getPublicStatus, publishStatus);
        toolVersionRepository.checkedUpdate(wrapper);
    }
}
