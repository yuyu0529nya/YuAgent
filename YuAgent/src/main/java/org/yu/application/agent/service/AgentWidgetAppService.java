package org.yu.application.agent.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yu.application.agent.assembler.AgentWidgetAssembler;
import org.yu.application.agent.dto.AgentWidgetDTO;
import org.yu.application.llm.assembler.ModelAssembler;
import org.yu.application.llm.assembler.ProviderAssembler;
import org.yu.application.llm.dto.ModelDTO;
import org.yu.application.llm.dto.ProviderDTO;
import org.yu.domain.agent.model.AgentEntity;
import org.yu.domain.agent.model.AgentWidgetEntity;
import org.yu.domain.agent.repository.AgentRepository;
import org.yu.domain.agent.service.AgentWidgetDomainService;
import org.yu.domain.agent.service.AgentWidgetUsageDomainService;
import org.yu.domain.llm.model.ModelEntity;
import org.yu.domain.llm.model.ProviderEntity;
import org.yu.domain.llm.service.LLMDomainService;
import org.yu.infrastructure.exception.BusinessException;
import org.yu.interfaces.dto.agent.request.CreateWidgetRequest;
import org.yu.interfaces.dto.agent.request.UpdateWidgetRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Agent小组件配置应用服务 */
@Service
public class AgentWidgetAppService {

    private final AgentWidgetDomainService agentWidgetDomainService;
    private final AgentRepository agentRepository;
    private final LLMDomainService llmDomainService;
    private final AgentWidgetAssembler agentWidgetAssembler;
    private final AgentWidgetUsageDomainService agentWidgetUsageDomainService;

    public AgentWidgetAppService(AgentWidgetDomainService agentWidgetDomainService, AgentRepository agentRepository,
            LLMDomainService llmDomainService, AgentWidgetAssembler agentWidgetAssembler,
            AgentWidgetUsageDomainService agentWidgetUsageDomainService) {
        this.agentWidgetDomainService = agentWidgetDomainService;
        this.agentRepository = agentRepository;
        this.llmDomainService = llmDomainService;
        this.agentWidgetAssembler = agentWidgetAssembler;
        this.agentWidgetUsageDomainService = agentWidgetUsageDomainService;
    }

    /** 创建小组件配置
     *
     * @param agentId Agent ID
     * @param request 创建请求
     * @param userId 用户ID
     * @return 创建的小组件配置DTO */
    @Transactional
    public AgentWidgetDTO createWidget(String agentId, CreateWidgetRequest request, String userId) {
        // 1. 验证Agent权限
        validateAgentOwnership(agentId, userId);

        // 2. 检查是否可以创建更多小组件配置（可选限制）
        // if (!agentWidgetDomainService.canCreateMoreWidgets(userId, 10)) {
        // throw new BusinessException("已达到最大小组件配置数量限制");
        // }

        // 3. 创建小组件配置实体
        AgentWidgetEntity widget = AgentWidgetAssembler.toEntity(request, agentId, userId);

        // 4. 保存到数据库
        AgentWidgetEntity savedWidget = agentWidgetDomainService.createWidget(widget);

        // 5. 获取关联的模型和服务商信息
        ModelEntity model = llmDomainService.getModelById(widget.getModelId());
        ProviderEntity provider = llmDomainService.getProvider(model.getProviderId());

        // 6. 转换为DTO并返回
        AgentWidgetDTO dto = agentWidgetAssembler.toDTOWithEmbedCode(savedWidget, ModelAssembler.toDTO(model),
                ProviderAssembler.toDTO(provider));
        dto.setDailyCalls(0);
        return dto;
    }

    /** 获取Agent的所有小组件配置
     *
     * @param agentId Agent ID
     * @param userId 用户ID
     * @return 小组件配置列表 */
    public List<AgentWidgetDTO> getWidgetsByAgent(String agentId, String userId) {
        // 1. 验证Agent权限
        validateAgentOwnership(agentId, userId);

        // 2. 获取小组件配置列表
        List<AgentWidgetEntity> widgets = agentWidgetDomainService.getWidgetsByAgent(agentId, userId);

        if (widgets.isEmpty()) {
            return List.of();
        }

        List<AgentWidgetDTO> dtos = assembleWidgetDtos(widgets);
        fillDailyCalls(dtos);
        return dtos;
    }

    /** 获取用户的所有小组件配置
     *
     * @param userId 用户ID
     * @return 小组件配置列表 */
    public List<AgentWidgetDTO> getWidgetsByUser(String userId) {
        List<AgentWidgetEntity> widgets = agentWidgetDomainService.getWidgetsByUser(userId);

        if (widgets.isEmpty()) {
            return List.of();
        }

        List<AgentWidgetDTO> dtos = assembleWidgetDtos(widgets);
        fillDailyCalls(dtos);
        return dtos;
    }

    /** 更新小组件配置
     *
     * @param widgetId 小组件配置ID
     * @param request 更新请求
     * @param userId 用户ID
     * @return 更新后的小组件配置DTO */
    @Transactional
    public AgentWidgetDTO updateWidget(String widgetId, UpdateWidgetRequest request, String userId) {
        AgentWidgetEntity widget = agentWidgetDomainService.getWidgetById(widgetId, userId);

        AgentWidgetAssembler.updateEntity(widget, request);

        AgentWidgetEntity updatedWidget = agentWidgetDomainService.updateWidget(widget, userId);

        ModelEntity model = llmDomainService.getModelById(widget.getModelId());
        ProviderEntity provider = llmDomainService.getProvider(model.getProviderId());

        AgentWidgetDTO dto = agentWidgetAssembler.toDTOWithEmbedCode(updatedWidget, ModelAssembler.toDTO(model),
                ProviderAssembler.toDTO(provider));
        dto.setDailyCalls(agentWidgetUsageDomainService.getTodayCallCount(updatedWidget.getId()));
        return dto;
    }

    /** 切换小组件配置启用状态
     *
     * @param widgetId 小组件配置ID
     * @param userId 用户ID
     * @return 更新后的小组件配置DTO */
    @Transactional
    public AgentWidgetDTO toggleWidgetStatus(String widgetId, String userId) {
        AgentWidgetEntity widget = agentWidgetDomainService.toggleWidgetStatus(widgetId, userId);
        ModelEntity model = llmDomainService.getModelById(widget.getModelId());
        ProviderEntity provider = llmDomainService.getProvider(model.getProviderId());

        AgentWidgetDTO dto = agentWidgetAssembler.toDTOWithEmbedCode(widget, ModelAssembler.toDTO(model),
                ProviderAssembler.toDTO(provider));
        dto.setDailyCalls(agentWidgetUsageDomainService.getTodayCallCount(widget.getId()));
        return dto;
    }

    /** 删除小组件配置
     *
     * @param widgetId 小组件配置ID
     * @param userId 用户ID */
    @Transactional
    public void deleteWidget(String widgetId, String userId) {
        agentWidgetDomainService.deleteWidget(widgetId, userId);
    }

    /** 获取小组件配置详情
     *
     * @param widgetId 小组件配置ID
     * @param userId 用户ID
     * @return 小组件配置DTO */
    public AgentWidgetDTO getWidgetDetail(String widgetId, String userId) {
        AgentWidgetEntity widget = agentWidgetDomainService.getWidgetById(widgetId, userId);

        ModelEntity model = llmDomainService.getModelById(widget.getModelId());
        ProviderEntity provider = llmDomainService.getProvider(model.getProviderId());

        AgentWidgetDTO dto = agentWidgetAssembler.toDTOWithEmbedCode(widget, ModelAssembler.toDTO(model),
                ProviderAssembler.toDTO(provider));
        dto.setDailyCalls(agentWidgetUsageDomainService.getTodayCallCount(widget.getId()));
        return dto;
    }

    /** 根据公开ID获取小组件配置（用于公开访问）
     *
     * @param publicId 公开访问ID
     * @return 小组件配置实体 */
    public AgentWidgetEntity getWidgetForPublicAccess(String publicId) {
        return agentWidgetDomainService.getEnabledWidgetByPublicId(publicId);
    }

    /** 验证域名访问权限
     *
     * @param publicId 公开访问ID
     * @param domain 访问域名
     * @return 是否允许访问 */
    public boolean validateDomainAccess(String publicId, String domain) {
        return agentWidgetDomainService.validateDomainAccess(publicId, domain);
    }

    /** 获取完整的小组件信息（包含Agent配置信息，用于公开访问）
     *
     * @param publicId 公开访问ID
     * @return 包含Agent配置信息的WidgetInfo */
    public WidgetInfoForPublicAccess getWidgetInfoForPublicAccess(String publicId) {
        // 1. 获取小组件配置
        AgentWidgetEntity widget = agentWidgetDomainService.getEnabledWidgetByPublicId(publicId);

        // 2. 获取关联的Agent信息
        AgentEntity agent = agentRepository.selectById(widget.getAgentId());
        if (agent == null || agent.getDeletedAt() != null) {
            throw new BusinessException("关联的Agent不存在");
        }

        // 3. 构建完整的信息对象
        WidgetInfoForPublicAccess info = new WidgetInfoForPublicAccess();

        // Widget基本信息
        info.setPublicId(widget.getPublicId());
        info.setName(widget.getName());
        info.setDescription(widget.getDescription());
        info.setDailyLimit(widget.getDailyLimit());
        info.setEnabled(widget.getEnabled());
        info.setDailyCalls(agentWidgetUsageDomainService.getTodayCallCount(widget.getId()));

        // Agent配置信息（用于无会话聊天）
        info.setAgentName(agent.getName());
        info.setAgentAvatar(agent.getAvatar());
        info.setWelcomeMessage(agent.getWelcomeMessage());
        info.setSystemPrompt(agent.getSystemPrompt());

        // 工具和知识库ID（需要转换为List<String>）
        if (agent.getToolIds() != null) {
            info.setToolIds(agent.getToolIds());
        }
        if (agent.getKnowledgeBaseIds() != null) {
            info.setKnowledgeBaseIds(agent.getKnowledgeBaseIds());
        }

        return info;
    }

    /** 完整的小组件信息类（用于公开访问） */
    public static class WidgetInfoForPublicAccess {
        private String publicId;
        private String name;
        private String description;
        private Integer dailyLimit;
        private Integer dailyCalls;
        private Boolean enabled;

        // Agent相关信息（用于无会话聊天）
        private String agentName;
        private String agentAvatar;
        private String welcomeMessage;
        private String systemPrompt;
        private List<String> toolIds;
        private List<String> knowledgeBaseIds;

        // Getter和Setter方法
        public String getPublicId() {
            return publicId;
        }

        public void setPublicId(String publicId) {
            this.publicId = publicId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Integer getDailyLimit() {
            return dailyLimit;
        }

        public void setDailyLimit(Integer dailyLimit) {
            this.dailyLimit = dailyLimit;
        }

        public Integer getDailyCalls() {
            return dailyCalls;
        }

        public void setDailyCalls(Integer dailyCalls) {
            this.dailyCalls = dailyCalls;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getAgentName() {
            return agentName;
        }

        public void setAgentName(String agentName) {
            this.agentName = agentName;
        }

        public String getAgentAvatar() {
            return agentAvatar;
        }

        public void setAgentAvatar(String agentAvatar) {
            this.agentAvatar = agentAvatar;
        }

        public String getWelcomeMessage() {
            return welcomeMessage;
        }

        public void setWelcomeMessage(String welcomeMessage) {
            this.welcomeMessage = welcomeMessage;
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }

        public List<String> getToolIds() {
            return toolIds;
        }

        public void setToolIds(List<String> toolIds) {
            this.toolIds = toolIds;
        }

        public List<String> getKnowledgeBaseIds() {
            return knowledgeBaseIds;
        }

        public void setKnowledgeBaseIds(List<String> knowledgeBaseIds) {
            this.knowledgeBaseIds = knowledgeBaseIds;
        }
    }

    // 私有辅助方法

    /** 验证Agent所有权 */
    private void validateAgentOwnership(String agentId, String userId) {
        AgentEntity agent = agentRepository.selectById(agentId);
        if (agent == null || agent.getDeletedAt() != null) {
            throw new BusinessException("Agent不存在");
        }

        if (!agent.getUserId().equals(userId)) {
            throw new BusinessException("无权限操作此Agent");
        }
    }
    public Integer consumeDailyCall(String publicId) {
        AgentWidgetEntity widget = agentWidgetDomainService.getEnabledWidgetByPublicId(publicId);
        return agentWidgetUsageDomainService.consumeDailyCall(widget.getId(), widget.getDailyLimit());
    }

    private void fillDailyCalls(List<AgentWidgetDTO> widgets) {
        if (widgets == null || widgets.isEmpty()) {
            return;
        }

        java.util.Map<String, Integer> usageMap = agentWidgetUsageDomainService
                .getTodayCallCounts(widgets.stream().map(AgentWidgetDTO::getId).toList());
        widgets.forEach(widget -> widget.setDailyCalls(usageMap.getOrDefault(widget.getId(), 0)));
    }

    private List<AgentWidgetDTO> assembleWidgetDtos(List<AgentWidgetEntity> widgets) {
        Set<String> modelIds = widgets.stream().map(AgentWidgetEntity::getModelId).filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<String, ModelEntity> modelsById = llmDomainService.getModelsByIds(modelIds).stream()
                .collect(Collectors.toMap(ModelEntity::getId, Function.identity()));
        Set<String> providerIds = modelsById.values().stream().map(ModelEntity::getProviderId).filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<String, ProviderEntity> providersById = llmDomainService.getProvidersByIds(providerIds).stream()
                .collect(Collectors.toMap(ProviderEntity::getId, Function.identity()));

        List<ModelDTO> models = new ArrayList<>(widgets.size());
        List<ProviderDTO> providers = new ArrayList<>(widgets.size());
        for (AgentWidgetEntity widget : widgets) {
            ModelEntity model = modelsById.get(widget.getModelId());
            ProviderEntity provider = model == null ? null : providersById.get(model.getProviderId());
            models.add(model == null ? null : ModelAssembler.toDTO(model));
            providers.add(provider == null ? null : ProviderAssembler.toDTO(provider));
        }
        return AgentWidgetAssembler.toDTOsWithEmbedCode(widgets, models, providers,
                agentWidgetAssembler.frontendBaseUrl);
    }
}
