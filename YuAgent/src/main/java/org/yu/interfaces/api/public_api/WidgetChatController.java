package org.yu.interfaces.api.public_api;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.yu.application.agent.service.AgentWidgetAppService;
import org.yu.application.conversation.dto.ChatResponse;
import org.yu.application.conversation.service.ConversationAppService;
import org.yu.domain.agent.model.AgentWidgetEntity;
import org.yu.infrastructure.exception.BusinessException;
import org.yu.interfaces.api.common.Result;
import org.yu.interfaces.dto.agent.request.WidgetChatRequest;

@RestController
@RequestMapping("/widget")
public class WidgetChatController {

    private final ConversationAppService conversationAppService;
    private final AgentWidgetAppService agentWidgetAppService;

    public WidgetChatController(ConversationAppService conversationAppService,
            AgentWidgetAppService agentWidgetAppService) {
        this.conversationAppService = conversationAppService;
        this.agentWidgetAppService = agentWidgetAppService;
    }

    @GetMapping("/{publicId}/info")
    public Result<WidgetInfoResponse> getWidgetInfo(@PathVariable String publicId, HttpServletRequest request) {
        try {
            String referer = request.getHeader("Referer");
            if (!validateDomainAccess(publicId, referer)) {
                return Result.forbidden("域名访问被拒绝");
            }

            AgentWidgetEntity widget = agentWidgetAppService.getWidgetForPublicAccess(publicId);
            AgentWidgetAppService.WidgetInfoForPublicAccess fullWidgetInfo = agentWidgetAppService
                    .getWidgetInfoForPublicAccess(publicId);

            WidgetInfoResponse response = new WidgetInfoResponse();
            response.setPublicId(widget.getPublicId());
            response.setName(widget.getName());
            response.setDescription(widget.getDescription());
            response.setDailyLimit(widget.getDailyLimit());
            response.setEnabled(widget.getEnabled());

            if (fullWidgetInfo != null) {
                response.setAgentName(fullWidgetInfo.getAgentName());
                response.setAgentAvatar(fullWidgetInfo.getAgentAvatar());
                response.setWelcomeMessage(fullWidgetInfo.getWelcomeMessage());
                response.setSystemPrompt(fullWidgetInfo.getSystemPrompt());
                response.setToolIds(fullWidgetInfo.getToolIds());
                response.setKnowledgeBaseIds(fullWidgetInfo.getKnowledgeBaseIds());
                response.setDailyCalls(fullWidgetInfo.getDailyCalls());
            }

            return Result.success(response);
        } catch (BusinessException e) {
            return Result.error(404, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, "获取小组件信息失败");
        }
    }

    @PostMapping("/{publicId}/chat")
    public SseEmitter widgetChat(@PathVariable String publicId, @RequestBody @Validated WidgetChatRequest request,
            HttpServletRequest httpRequest) {
        try {
            String referer = httpRequest.getHeader("Referer");
            if (!validateDomainAccess(publicId, referer)) {
                throw new BusinessException("域名访问被拒绝");
            }

            AgentWidgetEntity widget = agentWidgetAppService.getWidgetForPublicAccess(publicId);
            Integer dailyCalls = agentWidgetAppService.consumeDailyCall(publicId);
            if (dailyCalls == null) {
                throw new BusinessException("今日调用次数已达上限");
            }

            return conversationAppService.widgetChat(publicId, request, widget);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("聊天服务异常: " + e.getMessage());
        }
    }

    @PostMapping("/{publicId}/chat/sync")
    public Result<ChatResponse> widgetChatSync(@PathVariable String publicId,
            @RequestBody @Validated WidgetChatRequest request, HttpServletRequest httpRequest) {
        try {
            String referer = httpRequest.getHeader("Referer");
            if (!validateDomainAccess(publicId, referer)) {
                return Result.forbidden("域名访问被拒绝");
            }

            AgentWidgetEntity widget = agentWidgetAppService.getWidgetForPublicAccess(publicId);
            Integer dailyCalls = agentWidgetAppService.consumeDailyCall(publicId);
            if (dailyCalls == null) {
                return Result.error(429, "今日调用次数已达上限");
            }

            ChatResponse response = conversationAppService.widgetChatSync(publicId, request, widget);
            return Result.success(response);
        } catch (BusinessException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, "聊天服务异常: " + e.getMessage());
        }
    }

    private boolean validateDomainAccess(String publicId, String referer) {
        try {
            if (referer == null || referer.isEmpty()) {
                return true;
            }

            URL url = new URL(referer);
            String domain = url.getHost();
            return agentWidgetAppService.validateDomainAccess(publicId, domain);
        } catch (MalformedURLException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static class WidgetInfoResponse {
        private String publicId;
        private String name;
        private String description;
        private Integer dailyLimit;
        private Integer dailyCalls;
        private Boolean enabled;
        private String agentName;
        private String agentAvatar;
        private String welcomeMessage;
        private String systemPrompt;
        private List<String> toolIds;
        private List<String> knowledgeBaseIds;

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
}
