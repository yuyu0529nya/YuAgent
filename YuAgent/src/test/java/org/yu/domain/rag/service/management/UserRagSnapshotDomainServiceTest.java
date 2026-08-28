package org.yu.domain.rag.service.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.yu.domain.rag.model.RagVersionDocumentEntity;
import org.yu.domain.rag.model.RagVersionFileEntity;
import org.yu.domain.rag.model.UserRagDocumentEntity;
import org.yu.domain.rag.model.UserRagFileEntity;
import org.yu.domain.rag.repository.RagVersionDocumentRepository;
import org.yu.domain.rag.repository.RagVersionFileRepository;
import org.yu.domain.rag.repository.UserRagDocumentRepository;
import org.yu.domain.rag.repository.UserRagFileRepository;

class UserRagSnapshotDomainServiceTest {

    @Test
    void copiesFilesWithANewUserSnapshotId() {
        UserRagFileRepository userFileRepository = Mockito.mock(UserRagFileRepository.class);
        RagVersionFileRepository versionFileRepository = Mockito.mock(RagVersionFileRepository.class);
        UserRagSnapshotDomainService service = newService(userFileRepository,
                Mockito.mock(UserRagDocumentRepository.class), versionFileRepository,
                Mockito.mock(RagVersionDocumentRepository.class));

        when(versionFileRepository.selectList(Mockito.<LambdaQueryWrapper<RagVersionFileEntity>>any()))
                .thenReturn(List.of(versionFile("version-file-1", "original-file-1")));

        service.copyVersionFilesToUser("user-rag-1", "version-1");

        ArgumentCaptor<UserRagFileEntity> captured = ArgumentCaptor.forClass(UserRagFileEntity.class);
        verify(userFileRepository).insert(captured.capture());
        assertNull(captured.getValue().getId());
        assertEquals("user-rag-1", captured.getValue().getUserRagId());
        assertEquals("original-file-1", captured.getValue().getOriginalFileId());
    }

    @Test
    void mapsDocumentsToTheGeneratedUserFileId() {
        UserRagFileRepository userFileRepository = Mockito.mock(UserRagFileRepository.class);
        UserRagDocumentRepository userDocumentRepository = Mockito.mock(UserRagDocumentRepository.class);
        RagVersionFileRepository versionFileRepository = Mockito.mock(RagVersionFileRepository.class);
        RagVersionDocumentRepository versionDocumentRepository = Mockito.mock(RagVersionDocumentRepository.class);
        UserRagSnapshotDomainService service = newService(userFileRepository, userDocumentRepository,
                versionFileRepository, versionDocumentRepository);

        when(versionDocumentRepository.selectList(Mockito.<LambdaQueryWrapper<RagVersionDocumentEntity>>any()))
                .thenReturn(List.of(versionDocument("version-file-1")));
        when(versionFileRepository.selectList(Mockito.<LambdaQueryWrapper<RagVersionFileEntity>>any()))
                .thenReturn(List.of(versionFile("version-file-1", "original-file-1")));
        when(userFileRepository.selectList(Mockito.<LambdaQueryWrapper<UserRagFileEntity>>any()))
                .thenReturn(List.of(userFile("user-file-1", "original-file-1")));

        service.copyVersionDocumentsToUser("user-rag-1", "version-1");

        ArgumentCaptor<UserRagDocumentEntity> captured = ArgumentCaptor.forClass(UserRagDocumentEntity.class);
        verify(userDocumentRepository).insert(captured.capture());
        assertEquals("user-rag-1", captured.getValue().getUserRagId());
        assertEquals("user-file-1", captured.getValue().getUserRagFileId());
    }

    @Test
    void getUserRagDocumentCounts_shouldAggregateCountsInOneQuery() {
        UserRagDocumentRepository userDocumentRepository = Mockito.mock(UserRagDocumentRepository.class);
        UserRagSnapshotDomainService service = newService(Mockito.mock(UserRagFileRepository.class),
                userDocumentRepository, Mockito.mock(RagVersionFileRepository.class),
                Mockito.mock(RagVersionDocumentRepository.class));
        when(userDocumentRepository.selectMaps(Mockito.any()))
                .thenReturn(List.of(Map.of("user_rag_id", "user-rag-1", "document_count", 2L),
                        Map.of("user_rag_id", "user-rag-2", "document_count", 4L)));

        Map<String, Integer> counts = service.getUserRagDocumentCounts(List.of("user-rag-1", "user-rag-2"));

        assertEquals(Map.of("user-rag-1", 2, "user-rag-2", 4), counts);
        verify(userDocumentRepository).selectMaps(Mockito.any());
    }

    private UserRagSnapshotDomainService newService(UserRagFileRepository userFileRepository,
            UserRagDocumentRepository userDocumentRepository, RagVersionFileRepository versionFileRepository,
            RagVersionDocumentRepository versionDocumentRepository) {
        return new UserRagSnapshotDomainService(userFileRepository, userDocumentRepository, versionFileRepository,
                versionDocumentRepository);
    }

    private RagVersionFileEntity versionFile(String id, String originalFileId) {
        RagVersionFileEntity file = new RagVersionFileEntity();
        file.setId(id);
        file.setOriginalFileId(originalFileId);
        return file;
    }

    private UserRagFileEntity userFile(String id, String originalFileId) {
        UserRagFileEntity file = new UserRagFileEntity();
        file.setId(id);
        file.setOriginalFileId(originalFileId);
        return file;
    }

    private RagVersionDocumentEntity versionDocument(String versionFileId) {
        RagVersionDocumentEntity document = new RagVersionDocumentEntity();
        document.setRagVersionFileId(versionFileId);
        return document;
    }
}
