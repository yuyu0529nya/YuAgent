package org.yu.application.conversation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.yu.domain.agent.model.AgentEntity;
import org.yu.domain.agent.model.AgentVersionEntity;
import org.yu.infrastructure.exception.BusinessException;

class ConversationAppServiceVersionTest {

    @Test
    void appliesPublishedContentWithoutOverwritingAgentIdentity() {
        AgentEntity agent = new AgentEntity();
        agent.setId("agent-1");
        agent.setUserId("owner-1");
        agent.setPublishedVersion("version-1");
        agent.setEnabled(true);
        agent.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        agent.setAdmin();

        AgentVersionEntity version = new AgentVersionEntity();
        version.setId("version-1");
        version.setUserId("owner-2");
        version.setName("已发布助理");
        version.setSystemPrompt("使用版本提示词");
        version.setToolIds(List.of("tool-1"));
        version.setCreatedAt(LocalDateTime.of(2025, 2, 1, 0, 0));

        agent.applyPublishedVersion(version);

        assertEquals("agent-1", agent.getId());
        assertEquals("owner-1", agent.getUserId());
        assertEquals("version-1", agent.getPublishedVersion());
        assertEquals(true, agent.getEnabled());
        assertEquals(LocalDateTime.of(2025, 1, 1, 0, 0), agent.getCreatedAt());
        assertFalse(agent.needCheckUserId());
        assertEquals("已发布助理", agent.getName());
        assertEquals("使用版本提示词", agent.getSystemPrompt());
        assertEquals(List.of("tool-1"), agent.getToolIds());
    }

    @Test
    void rejectsAnInstalledAgentWithoutAnAvailableVersion() {
        assertThrows(BusinessException.class, () -> new AgentEntity().applyPublishedVersion(null));
    }
}
