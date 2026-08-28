package org.yu.domain.agent.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.yu.domain.agent.model.AgentWorkspaceEntity;
import org.yu.domain.agent.repository.AgentRepository;
import org.yu.domain.agent.repository.AgentWorkspaceRepository;

class AgentWorkspaceDomainServiceTest {

    @Test
    void shouldScopeWorkspaceUpdateToAgentAndUser() {
        AgentWorkspaceRepository workspaceRepository = mock(AgentWorkspaceRepository.class);
        AgentWorkspaceDomainService service = new AgentWorkspaceDomainService(workspaceRepository,
                mock(AgentRepository.class));
        AgentWorkspaceEntity workspace = new AgentWorkspaceEntity();
        workspace.setAgentId("agent-id");
        workspace.setUserId("user-id");

        service.update(workspace);

        ArgumentCaptor<LambdaUpdateWrapper<AgentWorkspaceEntity>> wrapperCaptor = ArgumentCaptor
                .forClass(LambdaUpdateWrapper.class);
        verify(workspaceRepository).checkedUpdate(eq(workspace), wrapperCaptor.capture());
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                AgentWorkspaceEntity.class);
        assertTrue(wrapperCaptor.getValue().getSqlSegment().contains("user_id"));
    }

    @Test
    void shouldUseRepositoryExistQueryForWorkspaceMembership() {
        AgentWorkspaceRepository workspaceRepository = mock(AgentWorkspaceRepository.class);
        AgentWorkspaceDomainService service = new AgentWorkspaceDomainService(workspaceRepository,
                mock(AgentRepository.class));
        when(workspaceRepository.exist("agent-id", "user-id")).thenReturn(true);

        assertTrue(service.exist("agent-id", "user-id"));
        verify(workspaceRepository).exist("agent-id", "user-id");
    }
}
