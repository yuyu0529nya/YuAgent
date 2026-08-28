package org.yu.application.rag.service.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yu.application.rag.dto.RagQaDatasetDTO;
import org.yu.domain.rag.constant.InstallType;
import org.yu.domain.rag.model.RagQaDatasetEntity;
import org.yu.domain.rag.model.UserRagEntity;
import org.yu.domain.rag.service.DocumentUnitDomainService;
import org.yu.domain.rag.service.EmbeddingDomainService;
import org.yu.domain.rag.service.FileDetailDomainService;
import org.yu.domain.rag.service.RagQaDatasetDomainService;
import org.yu.domain.rag.service.management.RagDataAccessDomainService;
import org.yu.domain.rag.service.management.RagVersionDomainService;
import org.yu.domain.rag.service.management.UserRagDomainService;
import org.yu.infrastructure.mq.core.MessagePublisher;
import org.yu.infrastructure.rag.service.RemoteFileImportService;
import org.yu.infrastructure.rag.service.UserModelConfigResolver;

class RagQaDatasetAppServiceTest {

    @Test
    void getUserAvailableDatasets_shouldBatchFileCountsByInstallType() {
        UserRagDomainService userRagDomainService = mock(UserRagDomainService.class);
        FileDetailDomainService fileDetailDomainService = mock(FileDetailDomainService.class);
        RagDataAccessDomainService ragDataAccessService = mock(RagDataAccessDomainService.class);
        RagQaDatasetAppService service = newService(userRagDomainService, fileDetailDomainService,
                ragDataAccessService);
        UserRagEntity snapshot = installedRag("snapshot-1", "dataset-snapshot", InstallType.SNAPSHOT);
        UserRagEntity reference = installedRag("reference-1", "dataset-reference", InstallType.REFERENCE);
        when(userRagDomainService.listAllInstalledRags("user-1")).thenReturn(List.of(snapshot, reference));
        when(ragDataAccessService.countUserRagFiles(List.of("snapshot-1"))).thenReturn(Map.of("snapshot-1", 2L));
        when(fileDetailDomainService.countFilesByDatasetsWithoutUserCheck(List.of("dataset-reference")))
                .thenReturn(Map.of("dataset-reference", 5L));

        List<RagQaDatasetDTO> datasets = service.getUserAvailableDatasets("user-1");

        assertEquals(2, datasets.size());
        assertEquals(2L, datasets.get(0).getFileCount());
        assertEquals(5L, datasets.get(1).getFileCount());
        verify(ragDataAccessService).countUserRagFiles(List.of("snapshot-1"));
        verify(fileDetailDomainService).countFilesByDatasetsWithoutUserCheck(List.of("dataset-reference"));
    }

    @Test
    void getDatasetsByIds_shouldBatchPermissionCheckedDatasetsAndPreserveRequestOrder() {
        RagQaDatasetDomainService datasetDomainService = mock(RagQaDatasetDomainService.class);
        FileDetailDomainService fileDetailDomainService = mock(FileDetailDomainService.class);
        RagQaDatasetAppService service = newService(datasetDomainService, mock(UserRagDomainService.class),
                fileDetailDomainService, mock(RagDataAccessDomainService.class));
        RagQaDatasetEntity first = dataset("dataset-1", "First");
        RagQaDatasetEntity second = dataset("dataset-2", "Second");
        when(datasetDomainService.listDatasetsByIdsForUser(List.of("dataset-2", "missing", "dataset-1"), "user-1"))
                .thenReturn(List.of(first, second));
        when(fileDetailDomainService.countFilesByDatasets(List.of("dataset-2", "missing", "dataset-1"), "user-1"))
                .thenReturn(Map.of("dataset-1", 1L, "dataset-2", 2L));

        List<RagQaDatasetDTO> datasets = service
                .getDatasetsByIds(List.of("dataset-2", "missing", "dataset-1", "dataset-2"), "user-1");

        assertEquals(List.of("Second", "First", "Second"), datasets.stream().map(RagQaDatasetDTO::getName).toList());
        assertEquals(List.of(2L, 1L, 2L), datasets.stream().map(RagQaDatasetDTO::getFileCount).toList());
        verify(datasetDomainService).listDatasetsByIdsForUser(List.of("dataset-2", "missing", "dataset-1"), "user-1");
        verify(fileDetailDomainService).countFilesByDatasets(List.of("dataset-2", "missing", "dataset-1"), "user-1");
    }

    private RagQaDatasetAppService newService(UserRagDomainService userRagDomainService,
            FileDetailDomainService fileDetailDomainService, RagDataAccessDomainService ragDataAccessService) {
        return newService(mock(RagQaDatasetDomainService.class), userRagDomainService, fileDetailDomainService,
                ragDataAccessService);
    }

    private RagQaDatasetAppService newService(RagQaDatasetDomainService datasetDomainService,
            UserRagDomainService userRagDomainService, FileDetailDomainService fileDetailDomainService,
            RagDataAccessDomainService ragDataAccessService) {
        return new RagQaDatasetAppService(datasetDomainService, fileDetailDomainService,
                mock(DocumentUnitDomainService.class), mock(MessagePublisher.class), mock(EmbeddingDomainService.class),
                mock(RagPublishAppService.class), mock(RagVersionDomainService.class), userRagDomainService,
                ragDataAccessService, mock(UserModelConfigResolver.class), mock(RemoteFileImportService.class));
    }

    private RagQaDatasetEntity dataset(String id, String name) {
        RagQaDatasetEntity entity = new RagQaDatasetEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setUserId("user-1");
        return entity;
    }

    private UserRagEntity installedRag(String id, String originalRagId, InstallType installType) {
        UserRagEntity entity = new UserRagEntity();
        entity.setId(id);
        entity.setOriginalRagId(originalRagId);
        entity.setInstallType(installType);
        entity.setName(id);
        return entity;
    }
}
