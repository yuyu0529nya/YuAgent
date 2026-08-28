package org.yu.domain.rag.service.management;

import org.junit.jupiter.api.Test;
import org.yu.domain.rag.constant.InstallType;
import org.yu.domain.rag.model.UserRagEntity;
import org.yu.domain.rag.repository.UserRagRepository;
import org.yu.domain.rag.service.RagQaDatasetDomainService;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRagDomainServiceTest {

    @Test
    void canUseRagDoesNotGrantAccessToAnUnownedAndUninstalledDataset() {
        UserRagRepository userRagRepository = mock(UserRagRepository.class);
        RagQaDatasetDomainService ragQaDatasetDomainService = mock(RagQaDatasetDomainService.class);
        when(ragQaDatasetDomainService.findDataset("rag-1", "user-1")).thenReturn(null);
        when(userRagRepository.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(null);

        UserRagDomainService service = new UserRagDomainService(userRagRepository, mock(RagVersionDomainService.class),
                ragQaDatasetDomainService, mock(UserRagSnapshotDomainService.class));

        assertFalse(service.canUseRag("user-1", "rag-1", null));
    }

    @Test
    void getInstallCountsConvertsGroupedQueryResultsToAVersionMap() {
        UserRagRepository userRagRepository = mock(UserRagRepository.class);
        when(userRagRepository.selectMaps(any()))
                .thenReturn(List.of(Map.of("rag_version_id", "version-1", "install_count", 4L)));
        UserRagDomainService service = new UserRagDomainService(userRagRepository, mock(RagVersionDomainService.class),
                mock(RagQaDatasetDomainService.class), mock(UserRagSnapshotDomainService.class));

        assertEquals(Map.of("version-1", 4L), service.getInstallCounts(List.of("version-1")));
    }

    @Test
    void getInstalledRagVersionIdsReturnsOnlyExistingVersionIds() {
        UserRagRepository userRagRepository = mock(UserRagRepository.class);
        when(userRagRepository.selectObjs(any())).thenReturn(List.of("version-1", "version-2"));
        UserRagDomainService service = new UserRagDomainService(userRagRepository, mock(RagVersionDomainService.class),
                mock(RagQaDatasetDomainService.class), mock(UserRagSnapshotDomainService.class));

        assertEquals(Set.of("version-1", "version-2"),
                service.getInstalledRagVersionIds("user-1", List.of("version-1", "version-2", "version-3")));
    }

    @Test
    void getInstalledRagsByOriginalIdsLoadsAllRequestedDatasetsInOneQuery() {
        UserRagRepository userRagRepository = mock(UserRagRepository.class);
        UserRagEntity first = userRag("user-rag-1", InstallType.REFERENCE);
        first.setOriginalRagId("rag-1");
        UserRagEntity second = userRag("user-rag-2", InstallType.SNAPSHOT);
        second.setOriginalRagId("rag-2");
        when(userRagRepository.selectList(any())).thenReturn(List.of(first, second));
        UserRagDomainService service = new UserRagDomainService(userRagRepository, mock(RagVersionDomainService.class),
                mock(RagQaDatasetDomainService.class), mock(UserRagSnapshotDomainService.class));

        Map<String, UserRagEntity> installedRags = service.getInstalledRagsByOriginalIds("user-1",
                List.of("rag-1", "rag-2", "rag-1", ""));

        assertEquals(Map.of("rag-1", first, "rag-2", second), installedRags);
        verify(userRagRepository).selectList(any());
    }

    @Test
    void forceUninstallAlsoRemovesSnapshotData() {
        UserRagRepository userRagRepository = mock(UserRagRepository.class);
        UserRagSnapshotDomainService snapshotService = mock(UserRagSnapshotDomainService.class);
        when(userRagRepository.selectList(any())).thenReturn(
                List.of(userRag("snapshot-1", InstallType.SNAPSHOT), userRag("reference-1", InstallType.REFERENCE)));
        doNothing().when(userRagRepository).checkedDelete(any());
        UserRagDomainService service = new UserRagDomainService(userRagRepository, mock(RagVersionDomainService.class),
                mock(RagQaDatasetDomainService.class), snapshotService);

        service.forceUninstallRagByOriginalId("user-1", "rag-1");

        verify(snapshotService).deleteUserSnapshot("snapshot-1");
        verify(snapshotService, never()).deleteUserSnapshot("reference-1");
        verify(userRagRepository).checkedDelete(any());
    }

    private UserRagEntity userRag(String id, InstallType installType) {
        UserRagEntity userRag = new UserRagEntity();
        userRag.setId(id);
        userRag.setInstallType(installType);
        return userRag;
    }
}
