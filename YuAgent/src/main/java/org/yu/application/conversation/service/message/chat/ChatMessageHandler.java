package org.yu.application.conversation.service.message.chat;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yu.application.conversation.service.message.AbstractMessageHandler;
import org.yu.application.conversation.service.message.builtin.BuiltInToolRegistry;
import org.yu.application.conversation.service.ChatSessionManager;
import org.yu.domain.conversation.service.MessageDomainService;
import org.yu.domain.conversation.service.SessionDomainService;
import org.yu.domain.llm.service.HighAvailabilityDomainService;
import org.yu.domain.llm.service.LLMDomainService;
import org.yu.domain.user.service.UserSettingsDomainService;
import org.yu.infrastructure.llm.LLMServiceFactory;
import org.yu.application.billing.service.BillingService;
import org.yu.domain.user.service.AccountDomainService;

/** 标准消息处理器 */
@Component(value = "chatMessageHandler")
public class ChatMessageHandler extends AbstractMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChatMessageHandler.class);

    public ChatMessageHandler(LLMServiceFactory llmServiceFactory, MessageDomainService messageDomainService,
            HighAvailabilityDomainService highAvailabilityDomainService, SessionDomainService sessionDomainService,
            UserSettingsDomainService userSettingsDomainService, LLMDomainService llmDomainService,
            BuiltInToolRegistry builtInToolRegistry, BillingService billingService,
            AccountDomainService accountDomainService, ChatSessionManager chatSessionManager) {
        super(llmServiceFactory, messageDomainService, highAvailabilityDomainService, sessionDomainService,
                userSettingsDomainService, llmDomainService, builtInToolRegistry, billingService, accountDomainService,
                chatSessionManager);
    }
}