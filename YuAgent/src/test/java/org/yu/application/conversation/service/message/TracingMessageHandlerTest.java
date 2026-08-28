package org.yu.application.conversation.service.message;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.yu.application.billing.service.BillingService;
import org.yu.application.conversation.service.ChatSessionManager;
import org.yu.application.conversation.service.handler.context.ChatContext;
import org.yu.application.conversation.service.message.builtin.BuiltInToolRegistry;
import org.yu.application.trace.collector.TraceCollector;
import org.yu.domain.conversation.model.MessageEntity;
import org.yu.domain.conversation.service.MessageDomainService;
import org.yu.domain.conversation.service.SessionDomainService;
import org.yu.domain.llm.service.HighAvailabilityDomainService;
import org.yu.domain.llm.service.LLMDomainService;
import org.yu.domain.trace.constant.ExecutionPhase;
import org.yu.domain.trace.model.TraceContext;
import org.yu.domain.user.service.AccountDomainService;
import org.yu.domain.user.service.UserSettingsDomainService;
import org.yu.infrastructure.llm.LLMServiceFactory;
import org.yu.infrastructure.transport.MessageTransport;

class TracingMessageHandlerTest {

    @Test
    void shouldClearTraceContextWhenUsingConnectionRegistrationCallback() throws Exception {
        CleanupTestHandler handler = new CleanupTestHandler();
        InheritableThreadLocal<TraceContext> traceHolder = traceHolder();
        traceHolder.set(TraceContext.create("user-1", "session-1", "agent-1"));

        try {
            handler.chat(new ChatContext(), new NoopTransport(), ignored -> {
            });

            assertNull(handler.currentTraceContext());
        } finally {
            traceHolder.remove();
        }
    }

    @SuppressWarnings("unchecked")
    private InheritableThreadLocal<TraceContext> traceHolder() throws Exception {
        Field field = TracingMessageHandler.class.getDeclaredField("currentTraceContext");
        field.setAccessible(true);
        return (InheritableThreadLocal<TraceContext>) field.get(null);
    }

    private static class CleanupTestHandler extends TracingMessageHandler {

        CleanupTestHandler() {
            super(mock(LLMServiceFactory.class), mock(MessageDomainService.class),
                    mock(HighAvailabilityDomainService.class), mock(SessionDomainService.class),
                    mock(UserSettingsDomainService.class), mock(LLMDomainService.class), mock(BuiltInToolRegistry.class),
                    mock(BillingService.class), mock(AccountDomainService.class), mock(ChatSessionManager.class),
                    mock(TraceCollector.class));
        }

        @Override
        protected <T> boolean checkBalanceBeforeChat(String userId, MessageTransport<T> transport, T connection) {
            return false;
        }

        @Override
        protected void onChatStart(ChatContext chatContext) {
        }

        @Override
        protected void onUserMessageProcessed(ChatContext chatContext, MessageEntity userMessage) {
        }

        @Override
        protected void onChatError(ChatContext chatContext, ExecutionPhase errorPhase, Throwable throwable) {
        }

        TraceContext currentTraceContext() {
            return getCurrentTraceContext();
        }
    }

    private static class NoopTransport implements MessageTransport<Object> {

        @Override
        public Object createConnection(long timeout) {
            return new Object();
        }

        @Override
        public void sendMessage(Object connection, org.yu.application.conversation.dto.AgentChatResponse response) {
        }

        @Override
        public void sendEndMessage(Object connection, org.yu.application.conversation.dto.AgentChatResponse response) {
        }

        @Override
        public void completeConnection(Object connection) {
        }

        @Override
        public void handleError(Object connection, Throwable error) {
        }
    }
}
