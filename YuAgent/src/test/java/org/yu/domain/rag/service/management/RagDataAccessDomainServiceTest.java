package org.yu.domain.rag.service.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.yu.domain.rag.constant.InstallType;
import org.yu.domain.rag.model.DocumentUnitEntity;
import org.yu.domain.rag.model.FileDetailEntity;
import org.yu.domain.rag.model.UserRagEntity;
import org.yu.domain.rag.repository.DocumentUnitRepository;
import org.yu.domain.rag.repository.FileDetailRepository;
import org.yu.domain.rag.repository.UserRagDocumentRepository;
import org.yu.domain.rag.repository.UserRagFileRepository;
import org.yu.domain.rag.repository.UserRagRepository;
import org.yu.infrastructure.exception.BusinessException;

class RagDataAccessDomainServiceTest {

    @Test
    void shouldLoadReferenceDocumentsFromFilesInTheInstalledDataset() {
        UserRagRepository userRagRepository = Mockito.mock(UserRagRepository.class);
        FileDetailRepository fileDetailRepository = Mockito.mock(FileDetailRepository.class);
        DocumentUnitRepository documentUnitRepository = Mockito.mock(DocumentUnitRepository.class);
        RagDataAccessDomainService service = newService(userRagRepository, fileDetailRepository,
                documentUnitRepository);

        when(userRagRepository.selectOne(Mockito.<LambdaQueryWrapper<UserRagEntity>>any())).thenReturn(referenceRag());
        when(fileDetailRepository.selectList(Mockito.<LambdaQueryWrapper<FileDetailEntity>>any()))
                .thenReturn(List.of(file("file-1"), file("file-2")));
        DocumentUnitEntity document = new DocumentUnitEntity();
        document.setFileId("file-1");
        when(documentUnitRepository.selectList(Mockito.<LambdaQueryWrapper<DocumentUnitEntity>>any()))
                .thenReturn(List.of(document));

        List<DocumentUnitEntity> documents = service.getRagDocuments("user-1", "user-rag-1");

        assertEquals(List.of(document), documents);
        verify(documentUnitRepository).selectList(Mockito.<LambdaQueryWrapper<DocumentUnitEntity>>any());
    }

    @Test
    void shouldRejectReferenceFileOutsideTheInstalledDataset() {
        UserRagRepository userRagRepository = Mockito.mock(UserRagRepository.class);
        FileDetailRepository fileDetailRepository = Mockito.mock(FileDetailRepository.class);
        DocumentUnitRepository documentUnitRepository = Mockito.mock(DocumentUnitRepository.class);
        RagDataAccessDomainService service = newService(userRagRepository, fileDetailRepository,
                documentUnitRepository);

        when(userRagRepository.selectOne(Mockito.<LambdaQueryWrapper<UserRagEntity>>any())).thenReturn(referenceRag());
        when(fileDetailRepository.selectOne(Mockito.<LambdaQueryWrapper<FileDetailEntity>>any())).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> service.getRagDocumentsByFile("user-1", "user-rag-1", "foreign-file"));
        verify(documentUnitRepository, never()).selectList(Mockito.<LambdaQueryWrapper<DocumentUnitEntity>>any());
    }

    @Test
    void countUserRagFiles_shouldAggregateCountsInOneQuery() {
        UserRagRepository userRagRepository = Mockito.mock(UserRagRepository.class);
        UserRagFileRepository userRagFileRepository = Mockito.mock(UserRagFileRepository.class);
        RagDataAccessDomainService service = new RagDataAccessDomainService(userRagRepository,
                Mockito.mock(FileDetailRepository.class), Mockito.mock(DocumentUnitRepository.class),
                userRagFileRepository, Mockito.mock(UserRagDocumentRepository.class));
        when(userRagFileRepository.selectMaps(Mockito.any()))
                .thenReturn(List.of(Map.of("user_rag_id", "user-rag-1", "file_count", 2L),
                        Map.of("user_rag_id", "user-rag-2", "file_count", 4L)));

        Map<String, Long> counts = service.countUserRagFiles(List.of("user-rag-1", "user-rag-2"));

        assertEquals(Map.of("user-rag-1", 2L, "user-rag-2", 4L), counts);
        verify(userRagFileRepository).selectMaps(Mockito.any());
    }

    private RagDataAccessDomainService newService(UserRagRepository userRagRepository,
            FileDetailRepository fileDetailRepository, DocumentUnitRepository documentUnitRepository) {
        return new RagDataAccessDomainService(userRagRepository, fileDetailRepository, documentUnitRepository,
                Mockito.mock(UserRagFileRepository.class), Mockito.mock(UserRagDocumentRepository.class));
    }

    private UserRagEntity referenceRag() {
        UserRagEntity userRag = new UserRagEntity();
        userRag.setId("user-rag-1");
        userRag.setUserId("user-1");
        userRag.setOriginalRagId("dataset-1");
        userRag.setInstallType(InstallType.REFERENCE);
        return userRag;
    }

    private FileDetailEntity file(String id) {
        FileDetailEntity file = new FileDetailEntity();
        file.setId(id);
        return file;
    }
}
