package org.yu.application.conversation.service.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.tool.ToolProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.yu.application.billing.service.BillingService;
import org.yu.application.conversation.dto.AgentChatResponse;
import org.yu.application.conversation.service.ChatSessionManager;
import org.yu.application.conversation.service.message.builtin.BuiltInToolRegistry;
import org.yu.application.conversation.service.message.rag.RagChatContext;
import org.yu.application.rag.dto.RagSearchRequest;
import org.yu.application.rag.service.search.RAGSearchAppService;
import org.yu.domain.conversation.model.ContextEntity;
import org.yu.domain.conversation.model.MessageEntity;
import org.yu.domain.conversation.service.MessageDomainService;
import org.yu.domain.conversation.service.SessionDomainService;
import org.yu.domain.llm.service.HighAvailabilityDomainService;
import org.yu.domain.llm.service.LLMDomainService;
import org.yu.domain.user.service.AccountDomainService;
import org.yu.domain.user.service.UserSettingsDomainService;
import org.yu.infrastructure.llm.LLMServiceFactory;
import org.yu.infrastructure.transport.MessageTransport;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RagMessageHandlerTest {

    private final LLMServiceFactory llmServiceFactory = mock(LLMServiceFactory.class);
    private final MessageDomainService messageDomainService = mock(MessageDomainService.class);
    private final RAGSearchAppService ragSearchAppService = mock(RAGSearchAppService.class);
    @SuppressWarnings("unchecked")
    private final MessageTransport<Object> transport = mock(MessageTransport.class);
    private final Object connection = new Object();

    private RagMessageHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RagMessageHandler(llmServiceFactory, messageDomainService,
                mock(HighAvailabilityDomainService.class), mock(SessionDomainService.class),
                mock(UserSettingsDomainService.class), mock(LLMDomainService.class), mock(BuiltInToolRegistry.class),
                mock(BillingService.class), mock(AccountDomainService.class), mock(ChatSessionManager.class),
                ragSearchAppService, new ObjectMapper());
    }

    @Test
    void shouldEndWithoutCallingModelWhenRagHasNoDocuments() {
        when(ragSearchAppService.ragSearch(any(RagSearchRequest.class), eq("user-1")))
                .thenReturn(Collections.emptyList());

        processRagChat();

        ArgumentCaptor<AgentChatResponse> responseCaptor = ArgumentCaptor.forClass(AgentChatResponse.class);
        verify(transport).sendEndMessage(eq(connection), responseCaptor.capture());
        assertTrue(responseCaptor.getValue().isDone());
        assertEquals("没有搜索到相关文档，可以换一个方式提问", responseCaptor.getValue().getContent());
        verify(messageDomainService).saveMessageAndUpdateContext(any(), any(ContextEntity.class));
        verifyNoInteractions(llmServiceFactory);
    }

    @Test
    void shouldTreatNullSearchResultsAsNoDocuments() {
        when(ragSearchAppService.ragSearch(any(RagSearchRequest.class), eq("user-1"))).thenReturn(null);

        processRagChat();

        ArgumentCaptor<AgentChatResponse> responseCaptor = ArgumentCaptor.forClass(AgentChatResponse.class);
        verify(transport).sendEndMessage(eq(connection), responseCaptor.capture());
        assertEquals("没有搜索到相关文档，可以换一个方式提问", responseCaptor.getValue().getContent());
        verify(messageDomainService).saveMessageAndUpdateContext(any(), any(ContextEntity.class));
        verifyNoInteractions(llmServiceFactory);
    }

    @Test
    void shouldEndWhenRagRetrievalFailsWithoutCallingModel() {
        when(ragSearchAppService.ragSearch(any(RagSearchRequest.class), eq("user-1")))
                .thenThrow(new IllegalStateException("search unavailable"));

        processRagChat();

        ArgumentCaptor<AgentChatResponse> responseCaptor = ArgumentCaptor.forClass(AgentChatResponse.class);
        verify(transport).sendEndMessage(eq(connection), responseCaptor.capture());
        assertTrue(responseCaptor.getValue().isDone());
        assertTrue(responseCaptor.getValue().getContent().contains("文档检索失败"));
        verify(messageDomainService).saveMessageAndUpdateContext(any(), any(ContextEntity.class));
        verifyNoInteractions(llmServiceFactory);
    }

    @Test
    void shouldCompleteTheStreamWhenProcessingFailsAfterRetrieval() {
        when(ragSearchAppService.ragSearch(any(RagSearchRequest.class), eq("user-1")))
                .thenReturn(Collections.emptyList());
        doThrow(new IllegalStateException("storage unavailable"))
                .when(messageDomainService).saveMessageAndUpdateContext(any(), any(ContextEntity.class));

        processRagChat();

        ArgumentCaptor<AgentChatResponse> responseCaptor = ArgumentCaptor.forClass(AgentChatResponse.class);
        verify(transport).sendEndMessage(eq(connection), responseCaptor.capture());
        assertTrue(responseCaptor.getValue().isDone());
        assertTrue(responseCaptor.getValue().getContent().contains("处理过程中发生错误"));
        verifyNoInteractions(llmServiceFactory);
    }

    private void processRagChat() {
        RagSearchRequest request = new RagSearchRequest();
        request.setDatasetIds(Collections.singletonList("dataset-1"));
        request.setQuestion("测试问题");
        RagChatContext context = RagChatContext.builder().userId("user-1").userMessage("测试问题").ragSearchRequest(request)
                .contextEntity(mock(ContextEntity.class)).build();

        handler.processStreamingChat(context, connection, transport, new MessageEntity(), new MessageEntity(),
                mock(MessageWindowChatMemory.class), mock(ToolProvider.class));
    }
}
