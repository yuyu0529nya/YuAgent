package org.yu.application.conversation.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.yu.application.conversation.service.handler.context.ChatContext;
import org.yu.domain.conversation.service.MessageDomainService;
import org.yu.domain.conversation.service.SessionDomainService;
import org.yu.domain.llm.service.HighAvailabilityDomainService;
import org.yu.domain.llm.service.LLMDomainService;
import org.yu.domain.user.service.UserSettingsDomainService;
import org.yu.infrastructure.llm.LLMServiceFactory;

class SessionTitleGenerationServiceTest {

    @Test
    void shouldSkipGenerationWhenSessionAlreadyHasMessages() {
        MessageDomainService messageDomainService = mock(MessageDomainService.class);
        UserSettingsDomainService settingsDomainService = mock(UserSettingsDomainService.class);
        LLMDomainService llmDomainService = mock(LLMDomainService.class);
        HighAvailabilityDomainService highAvailabilityDomainService = mock(HighAvailabilityDomainService.class);
        LLMServiceFactory llmServiceFactory = mock(LLMServiceFactory.class);
        SessionDomainService sessionDomainService = mock(SessionDomainService.class);
        SessionTitleGenerationService service = new SessionTitleGenerationService(messageDomainService,
                settingsDomainService, llmDomainService, highAvailabilityDomainService, llmServiceFactory,
                sessionDomainService);
        ChatContext chatContext = new ChatContext();
        chatContext.setSessionId("session-id");
        chatContext.setUserId("user-id");
        chatContext.setUserMessage("hello");
        when(messageDomainService.isFirstConversation("session-id")).thenReturn(false);

        service.generateTitle(chatContext);

        verify(messageDomainService).isFirstConversation("session-id");
        verifyNoInteractions(settingsDomainService, llmDomainService, highAvailabilityDomainService, llmServiceFactory,
                sessionDomainService);
    }
}
