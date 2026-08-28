package org.yu.application.conversation.service.message.builtin.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yu.application.conversation.service.message.builtin.ToolDefinition;
import org.yu.application.rag.service.search.RAGSearchAppService;
import org.yu.domain.agent.model.AgentEntity;
import org.yu.domain.rag.model.UserRagEntity;
import org.yu.domain.rag.service.management.UserRagDomainService;

class RagBuiltInToolProviderTest {

    @Test
    void defineToolsUsesOneBatchLookupForConfiguredKnowledgeBases() {
        UserRagDomainService userRagDomainService = mock(UserRagDomainService.class);
        UserRagEntity first = installedRag("rag-1", "产品文档");
        UserRagEntity second = installedRag("rag-2", "常见问题");
        when(userRagDomainService.getInstalledRagsByOriginalIds("user-1", List.of("rag-1", "rag-2", "missing")))
                .thenReturn(Map.of("rag-1", first, "rag-2", second));

        RagBuiltInToolProvider provider = new RagBuiltInToolProvider(mock(RAGSearchAppService.class),
                userRagDomainService);
        AgentEntity agent = new AgentEntity();
        agent.setId("agent-1");
        agent.setUserId("user-1");
        agent.setKnowledgeBaseIds(List.of("rag-1", "rag-2", "missing"));

        List<ToolDefinition> definitions = provider.defineTools(agent);

        assertEquals(1, definitions.size());
        assertEquals("knowledge_search", definitions.get(0).getName());
        assertTrue(definitions.get(0).getDescription().contains("产品文档、常见问题"));
        verify(userRagDomainService).getInstalledRagsByOriginalIds("user-1", List.of("rag-1", "rag-2", "missing"));
    }

    private UserRagEntity installedRag(String originalRagId, String name) {
        UserRagEntity userRag = new UserRagEntity();
        userRag.setOriginalRagId(originalRagId);
        userRag.setName(name);
        return userRag;
    }
}
