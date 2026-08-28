package org.yu.application.conversation.service.message.agent;

import dev.langchain4j.service.tool.ToolProvider;
import org.springframework.stereotype.Component;
import org.yu.application.billing.service.BillingService;
import org.yu.application.conversation.service.ChatSessionManager;
import org.yu.application.conversation.service.handler.context.ChatContext;
import org.yu.application.conversation.service.message.TracingMessageHandler;
import org.yu.application.conversation.service.message.builtin.BuiltInToolRegistry;
import org.yu.application.trace.collector.TraceCollector;
import org.yu.domain.conversation.service.MessageDomainService;
import org.yu.domain.conversation.service.SessionDomainService;
import org.yu.domain.llm.service.HighAvailabilityDomainService;
import org.yu.domain.llm.service.LLMDomainService;
import org.yu.domain.user.service.AccountDomainService;
import org.yu.domain.user.service.UserSettingsDomainService;
import org.yu.infrastructure.llm.LLMServiceFactory;

@Component(value = "agentMessageHandler")
public class AgentMessageHandler extends TracingMessageHandler {

    private final AgentToolManager agentToolManager;

    public AgentMessageHandler(LLMServiceFactory llmServiceFactory, MessageDomainService messageDomainService,
            HighAvailabilityDomainService highAvailabilityDomainService, SessionDomainService sessionDomainService,
            UserSettingsDomainService userSettingsDomainService, LLMDomainService llmDomainService,
            BuiltInToolRegistry builtInToolRegistry, BillingService billingService,
            AccountDomainService accountDomainService, ChatSessionManager chatSessionManager,
            TraceCollector traceCollector, AgentToolManager agentToolManager) {
        super(llmServiceFactory, messageDomainService, highAvailabilityDomainService, sessionDomainService,
                userSettingsDomainService, llmDomainService, builtInToolRegistry, billingService, accountDomainService,
                chatSessionManager, traceCollector);
        this.agentToolManager = agentToolManager;
    }

    @Override
    protected ToolProvider provideTools(ChatContext chatContext) {
        return agentToolManager.createToolProvider(chatContext);
    }
}
