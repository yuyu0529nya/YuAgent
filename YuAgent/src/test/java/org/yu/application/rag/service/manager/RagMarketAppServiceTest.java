package org.yu.application.rag.service.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yu.application.rag.dto.UserRagDTO;
import org.yu.domain.rag.constant.InstallType;
import org.yu.domain.rag.model.RagQaDatasetEntity;
import org.yu.domain.rag.model.RagVersionEntity;
import org.yu.domain.rag.model.UserRagEntity;
import org.yu.domain.rag.service.FileDetailDomainService;
import org.yu.domain.rag.service.RagQaDatasetDomainService;
import org.yu.domain.rag.service.management.RagDataAccessDomainService;
import org.yu.domain.rag.service.management.RagVersionDomainService;
import org.yu.domain.rag.service.management.UserRagDomainService;
import org.yu.domain.rag.service.management.UserRagSnapshotDomainService;
import org.yu.domain.user.model.UserEntity;
import org.yu.domain.user.service.UserDomainService;

class RagMarketAppServiceTest {

    @Test
    void getUserAllInstalledRags_shouldBatchEnrichSnapshotAndReferenceRecords() {
        UserRagDomainService userRagDomainService = mock(UserRagDomainService.class);
        UserDomainService userDomainService = mock(UserDomainService.class);
        RagQaDatasetDomainService datasetDomainService = mock(RagQaDatasetDomainService.class);
        UserRagSnapshotDomainService snapshotDomainService = mock(UserRagSnapshotDomainService.class);
        RagDataAccessDomainService ragDataAccessService = mock(RagDataAccessDomainService.class);
        RagVersionDomainService ragVersionDomainService = mock(RagVersionDomainService.class);
        RagMarketAppService service = new RagMarketAppService(ragVersionDomainService, userRagDomainService,
                userDomainService, datasetDomainService, snapshotDomainService, ragDataAccessService,
                mock(FileDetailDomainService.class));
        UserRagEntity snapshot = installedRag("snapshot-1", "version-1", "dataset-snapshot", InstallType.SNAPSHOT);
        UserRagEntity reference = installedRag("reference-1", null, "dataset-reference", InstallType.REFERENCE);
        when(userRagDomainService.listAllInstalledRags("user-1")).thenReturn(List.of(snapshot, reference));
        when(ragDataAccessService.countUserRagFiles(List.of("snapshot-1"))).thenReturn(Map.of("snapshot-1", 2L));
        when(snapshotDomainService.getUserRagDocumentCounts(List.of("snapshot-1"))).thenReturn(Map.of("snapshot-1", 3));
        RagQaDatasetEntity dataset = new RagQaDatasetEntity();
        dataset.setId("dataset-reference");
        dataset.setUserId("user-1");
        dataset.setName("Reference");
        when(datasetDomainService.listDatasetsByIds(List.of("dataset-reference"))).thenReturn(List.of(dataset));
        RagVersionEntity version = new RagVersionEntity();
        version.setId("version-1");
        version.setUserId("creator-1");
        when(ragVersionDomainService.getRagVersionsByIds(List.of("version-1"))).thenReturn(List.of(version));
        UserEntity creator = new UserEntity();
        creator.setId("creator-1");
        creator.setNickname("Creator");
        when(userDomainService.getByIds(List.of("user-1", "creator-1"))).thenReturn(List.of(creator));

        List<UserRagDTO> result = service.getUserAllInstalledRags("user-1");

        assertEquals(2, result.size());
        assertEquals(2, result.get(0).getFileCount());
        assertEquals(3, result.get(0).getDocumentCount());
        verify(ragDataAccessService).countUserRagFiles(List.of("snapshot-1"));
        verify(snapshotDomainService).getUserRagDocumentCounts(List.of("snapshot-1"));
        verify(datasetDomainService).listDatasetsByIds(List.of("dataset-reference"));
        verify(ragVersionDomainService).getRagVersionsByIds(List.of("version-1"));
    }

    private UserRagEntity installedRag(String id, String versionId, String originalRagId, InstallType installType) {
        UserRagEntity entity = new UserRagEntity();
        entity.setId(id);
        entity.setUserId("user-1");
        entity.setRagVersionId(versionId);
        entity.setOriginalRagId(originalRagId);
        entity.setInstallType(installType);
        entity.setName(id);
        return entity;
    }
}
