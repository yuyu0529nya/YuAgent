package org.yu.application.rag.service.manager;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.yu.application.rag.dto.RagVersionDTO;
import org.yu.domain.rag.model.RagVersionEntity;
import org.yu.domain.rag.service.management.RagVersionDomainService;
import org.yu.domain.rag.service.management.UserRagDomainService;
import org.yu.domain.user.model.UserEntity;
import org.yu.domain.user.service.UserDomainService;
import org.yu.interfaces.dto.rag.request.QueryUserRagVersionRequest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagPublishAppServiceTest {

    @Test
    void getUserRagVersionsLoadsCreatorsAndInstallCountsInBatches() {
        RagVersionDomainService ragVersionDomainService = mock(RagVersionDomainService.class);
        UserRagDomainService userRagDomainService = mock(UserRagDomainService.class);
        UserDomainService userDomainService = mock(UserDomainService.class);
        Page<RagVersionEntity> page = new Page<>(1, 15, 2);
        page.setRecords(List.of(version("version-1", "user-1"), version("version-2", "user-2")));
        when(ragVersionDomainService.listUserVersions("user-1", 1, 15, null)).thenReturn(page);
        when(userRagDomainService.getInstallCounts(anyList())).thenReturn(Map.of("version-1", 3L, "version-2", 5L));
        when(userDomainService.getByIds(anyList())).thenReturn(List.of(user("user-1", "Alice"), user("user-2", "Bob")));

        RagPublishAppService service = new RagPublishAppService(ragVersionDomainService, userRagDomainService,
                userDomainService);
        Page<RagVersionDTO> result = service.getUserRagVersions("user-1", new QueryUserRagVersionRequest());

        assertEquals(List.of("Alice", "Bob"),
                result.getRecords().stream().map(RagVersionDTO::getUserNickname).toList());
        assertEquals(List.of(3L, 5L), result.getRecords().stream().map(RagVersionDTO::getInstallCount).toList());
        verify(userDomainService).getByIds(anyList());
        verify(userRagDomainService).getInstallCounts(anyList());
        verify(userDomainService, never()).getUserInfo("user-1");
        verify(userRagDomainService, never()).getInstallCount("version-1");
    }

    private RagVersionEntity version(String id, String userId) {
        RagVersionEntity version = new RagVersionEntity();
        version.setId(id);
        version.setUserId(userId);
        return version;
    }

    private UserEntity user(String id, String nickname) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setNickname(nickname);
        return user;
    }
}
