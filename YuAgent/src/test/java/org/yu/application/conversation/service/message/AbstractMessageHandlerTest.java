package org.yu.application.conversation.service.message;

import org.junit.jupiter.api.Test;
import org.yu.application.billing.service.BillingService;
import org.yu.application.conversation.service.ChatSessionManager;
import org.yu.application.conversation.service.message.builtin.BuiltInToolRegistry;
import org.yu.domain.conversation.service.MessageDomainService;
import org.yu.domain.conversation.service.SessionDomainService;
import org.yu.domain.llm.service.HighAvailabilityDomainService;
import org.yu.domain.llm.service.LLMDomainService;
import org.yu.domain.user.service.AccountDomainService;
import org.yu.domain.user.service.UserSettingsDomainService;
import org.yu.infrastructure.llm.LLMServiceFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

class AbstractMessageHandlerTest {

    @Test
    void shouldBuildStableBillingRequestIdFromUserMessageId() {
        TestMessageHandler handler = new TestMessageHandler();

        String first = handler.generateRequestId("session-1", "user-1", "message-1");
        String second = handler.generateRequestId("session-1", "user-1", "message-1");

        assertEquals(first, second);
    }

    @Test
    void shouldUseDifferentBillingRequestIdsForDifferentMessages() {
        TestMessageHandler handler = new TestMessageHandler();

        String first = handler.generateRequestId("session-1", "user-1", "message-1");
        String second = handler.generateRequestId("session-1", "user-1", "message-2");

        assertNotEquals(first, second);
    }

    private static class TestMessageHandler extends AbstractMessageHandler {

        TestMessageHandler() {
            super(mock(LLMServiceFactory.class), mock(MessageDomainService.class), mock(HighAvailabilityDomainService.class),
                    mock(SessionDomainService.class), mock(UserSettingsDomainService.class), mock(LLMDomainService.class),
                    mock(BuiltInToolRegistry.class), mock(BillingService.class), mock(AccountDomainService.class),
                    mock(ChatSessionManager.class));
        }
    }
}
