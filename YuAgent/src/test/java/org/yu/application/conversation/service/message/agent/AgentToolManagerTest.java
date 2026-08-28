package org.yu.application.conversation.service.message.agent;

import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentToolManagerTest {

    @Test
    void shouldCloseEachMcpClientOnlyOnce() throws Exception {
        McpClient firstClient = mock(McpClient.class);
        McpClient secondClient = mock(McpClient.class);
        ToolProvider delegate = mock(ToolProvider.class);
        AgentToolManager.CloseableToolProvider provider = new AgentToolManager.CloseableToolProvider(delegate,
                List.of(firstClient, secondClient));

        provider.close();
        provider.close();

        verify(firstClient).close();
        verify(secondClient).close();
    }
}
