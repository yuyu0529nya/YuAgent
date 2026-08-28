package org.yu.application.conversation.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.yu.application.conversation.service.handler.context.AgentPromptTemplates;
import org.yu.application.conversation.service.handler.context.ChatContext;
import org.yu.domain.conversation.service.MessageDomainService;
import org.yu.domain.conversation.service.SessionDomainService;
import org.yu.domain.llm.model.HighAvailabilityResult;
import org.yu.domain.llm.model.ModelEntity;
import org.yu.domain.llm.model.ProviderEntity;
import org.yu.domain.llm.service.HighAvailabilityDomainService;
import org.yu.domain.llm.service.LLMDomainService;
import org.yu.domain.user.service.UserSettingsDomainService;
import org.yu.infrastructure.llm.LLMServiceFactory;

import java.util.ArrayList;
import java.util.List;

/** 异步生成首次会话标题，失败不影响主对话链路。 */
@Service
public class SessionTitleGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(SessionTitleGenerationService.class);
    private static final int MAX_TITLE_LENGTH = 100;

    private final MessageDomainService messageDomainService;
    private final UserSettingsDomainService userSettingsDomainService;
    private final LLMDomainService llmDomainService;
    private final HighAvailabilityDomainService highAvailabilityDomainService;
    private final LLMServiceFactory llmServiceFactory;
    private final SessionDomainService sessionDomainService;

    public SessionTitleGenerationService(MessageDomainService messageDomainService,
            UserSettingsDomainService userSettingsDomainService, LLMDomainService llmDomainService,
            HighAvailabilityDomainService highAvailabilityDomainService, LLMServiceFactory llmServiceFactory,
            SessionDomainService sessionDomainService) {
        this.messageDomainService = messageDomainService;
        this.userSettingsDomainService = userSettingsDomainService;
        this.llmDomainService = llmDomainService;
        this.highAvailabilityDomainService = highAvailabilityDomainService;
        this.llmServiceFactory = llmServiceFactory;
        this.sessionDomainService = sessionDomainService;
    }

    @Async("sessionTitleTaskExecutor")
    public void generateTitle(ChatContext chatContext) {
        if (chatContext == null || StringUtils.isBlank(chatContext.getSessionId())
                || StringUtils.isBlank(chatContext.getUserId()) || StringUtils.isBlank(chatContext.getUserMessage())
                || !messageDomainService.isFirstConversation(chatContext.getSessionId())) {
            return;
        }

        try {
            String userId = chatContext.getUserId();
            String modelId = userSettingsDomainService.getUserDefaultModelId(userId);
            ModelEntity model = llmDomainService.getModelById(modelId);
            List<String> fallbackChain = userSettingsDomainService.getUserFallbackChain(userId);
            HighAvailabilityResult result = highAvailabilityDomainService.selectBestProvider(model, userId,
                    chatContext.getSessionId(), fallbackChain);
            ProviderEntity provider = result.getProvider();
            ChatModel chatModel = llmServiceFactory.getStrandClient(provider, result.getModel());
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new SystemMessage(AgentPromptTemplates.getStartConversationPrompt()));
            messages.add(new UserMessage(chatContext.getUserMessage()));
            ChatResponse response = chatModel.chat(messages);
            String title = normalizeTitle(response.aiMessage().text());

            if (StringUtils.isNotBlank(title)) {
                sessionDomainService.updateSession(chatContext.getSessionId(), userId, title);
            }
        } catch (Exception e) {
            logger.warn("生成会话标题失败: sessionId={}, error={}", chatContext.getSessionId(), e.getMessage());
        }
    }

    private String normalizeTitle(String title) {
        if (StringUtils.isBlank(title)) {
            return null;
        }

        String normalized = title.replaceAll("\\s+", " ").trim();
        return StringUtils.abbreviate(normalized, MAX_TITLE_LENGTH);
    }
}
