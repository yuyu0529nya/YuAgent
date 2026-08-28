package org.yu.application.conversation.service.message;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.yu.application.billing.service.BillingService;
import org.yu.application.conversation.service.ChatSessionManager;
import org.yu.application.conversation.service.handler.context.ChatContext;
import org.yu.application.conversation.service.message.builtin.BuiltInToolRegistry;
import org.yu.domain.conversation.constant.MessageType;
import org.yu.domain.conversation.service.MessageDomainService;
import org.yu.domain.conversation.service.SessionDomainService;
import org.yu.domain.llm.service.HighAvailabilityDomainService;
import org.yu.domain.llm.service.LLMDomainService;
import org.yu.domain.user.model.AccountEntity;
import org.yu.domain.user.service.AccountDomainService;
import org.yu.domain.user.service.UserSettingsDomainService;
import org.yu.infrastructure.exception.InsufficientBalanceException;
import org.yu.infrastructure.llm.LLMServiceFactory;
import org.yu.infrastructure.transport.MessageTransport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void shouldEndConnectionWhenAccountBalanceIsNegative() {
        AccountDomainService accountDomainService = mock(AccountDomainService.class);
        AccountEntity account = AccountEntity.createNew("user-1");
        account.setBalance(BigDecimal.valueOf(-1));
        when(accountDomainService.getOrCreateAccount("user-1")).thenReturn(account);

        TestMessageHandler handler = new TestMessageHandler(accountDomainService);
        MessageTransport<String> transport = mockTransport();

        boolean canContinue = handler.checkBalance("user-1", transport, "connection-1");

        assertFalse(canContinue);
        verify(transport).sendEndMessage(eq("connection-1"),
                org.mockito.ArgumentMatchers.argThat(response -> response.isDone()
                        && response.getMessageType() == MessageType.TEXT && response.getContent().contains("当前余额：-1")));
        verify(transport, never()).sendMessage(eq("connection-1"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldEndConnectionWhenAccountServiceRejectsBalance() {
        AccountDomainService accountDomainService = mock(AccountDomainService.class);
        when(accountDomainService.getOrCreateAccount("user-1")).thenThrow(new InsufficientBalanceException("账户余额不足"));

        TestMessageHandler handler = new TestMessageHandler(accountDomainService);
        MessageTransport<String> transport = mockTransport();

        boolean canContinue = handler.checkBalance("user-1", transport, "connection-1");

        assertFalse(canContinue);
        verify(transport).sendEndMessage(eq("connection-1"),
                org.mockito.ArgumentMatchers.argThat(response -> response.isDone()
                        && response.getMessageType() == MessageType.TEXT && "账户余额不足".equals(response.getContent())));
    }

    @Test
    void shouldEndConnectionWhenChatInitializationFails() {
        TestMessageHandler handler = new TestMessageHandler(true);
        MessageTransport<String> transport = mockTransport();
        ChatContext chatContext = mock(ChatContext.class);
        when(transport.createConnection(AbstractMessageHandler.CONNECTION_TIMEOUT)).thenReturn("connection-1");
        when(chatContext.getUserId()).thenReturn("user-1");
        when(chatContext.getSessionId()).thenReturn("session-1");

        String connection = handler.chat(chatContext, transport);

        assertEquals("connection-1", connection);
        verify(transport).sendEndMessage(eq("connection-1"),
                org.mockito.ArgumentMatchers
                        .argThat(response -> response.isDone() && response.getMessageType() == MessageType.TEXT
                                && "initialization failed".equals(response.getContent())));
    }

    @Test
    void shouldInitializeConnectionBeforeStartingChatWork() {
        MessageTransport<String> transport = mockTransport();
        ChatContext chatContext = mock(ChatContext.class);
        AtomicBoolean initialized = new AtomicBoolean(false);
        AtomicBoolean initializedBeforeChatStart = new AtomicBoolean(false);
        TestMessageHandler handler = new TestMessageHandler() {
            @Override
            protected void onChatStart(ChatContext context) {
                initializedBeforeChatStart.set(initialized.get());
                throw new IllegalStateException("initialization failed");
            }
        };
        when(transport.createConnection(AbstractMessageHandler.CONNECTION_TIMEOUT)).thenReturn("connection-1");
        when(chatContext.getUserId()).thenReturn("user-1");
        when(chatContext.getSessionId()).thenReturn("session-1");

        handler.chat(chatContext, transport, ignored -> initialized.set(true));

        assertTrue(initialized.get());
        assertTrue(initializedBeforeChatStart.get());
    }

    @Test
    void shouldStopStreamingWhenSessionIsInterrupted() {
        ChatSessionManager chatSessionManager = mock(ChatSessionManager.class);
        when(chatSessionManager.isSessionInterrupted("session-1")).thenReturn(true);
        TestMessageHandler handler = new TestMessageHandler(mock(AccountDomainService.class), false,
                chatSessionManager);
        ChatContext chatContext = mock(ChatContext.class);
        when(chatContext.getSessionId()).thenReturn("session-1");

        assertTrue(handler.shouldStop(chatContext));
    }

    private static class TestMessageHandler extends AbstractMessageHandler {
        private final boolean failOnChatStart;

        TestMessageHandler() {
            this(mock(AccountDomainService.class), false, mock(ChatSessionManager.class));
        }

        TestMessageHandler(AccountDomainService accountDomainService) {
            this(accountDomainService, false, mock(ChatSessionManager.class));
        }

        TestMessageHandler(boolean failOnChatStart) {
            this(mock(AccountDomainService.class), failOnChatStart, mock(ChatSessionManager.class));
        }

        TestMessageHandler(AccountDomainService accountDomainService, boolean failOnChatStart,
                ChatSessionManager chatSessionManager) {
            super(mock(LLMServiceFactory.class), mock(MessageDomainService.class),
                    mock(HighAvailabilityDomainService.class), mock(SessionDomainService.class),
                    mock(UserSettingsDomainService.class), mock(LLMDomainService.class),
                    mock(BuiltInToolRegistry.class), mock(BillingService.class), accountDomainService,
                    chatSessionManager);
            this.failOnChatStart = failOnChatStart;
        }

        boolean checkBalance(String userId, MessageTransport<String> transport, String connection) {
            return checkBalanceBeforeChat(userId, transport, connection);
        }

        boolean shouldStop(ChatContext chatContext) {
            return shouldStopStreaming(chatContext, new AtomicBoolean(false));
        }

        @Override
        protected void onChatStart(ChatContext chatContext) {
            if (failOnChatStart) {
                throw new IllegalStateException("initialization failed");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static MessageTransport<String> mockTransport() {
        return mock(MessageTransport.class);
    }
}
