package org.yu.domain.rag.service.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.yu.application.rag.dto.RagContentPreviewDTO;
import org.yu.domain.rag.model.RagVersionDocumentEntity;
import org.yu.domain.rag.model.RagVersionEntity;
import org.yu.domain.rag.model.RagVersionFileEntity;
import org.yu.domain.rag.repository.DocumentUnitRepository;
import org.yu.domain.rag.repository.FileDetailRepository;
import org.yu.domain.rag.repository.RagVersionDocumentRepository;
import org.yu.domain.rag.repository.RagVersionFileRepository;
import org.yu.domain.rag.repository.RagVersionRepository;
import org.yu.domain.rag.service.RagQaDatasetDomainService;

class RagVersionDomainServiceTest {

    @Test
    void getRagContentPreviewUsesTheLoadedFileListForDocumentNames() {
        RagVersionRepository versionRepository = Mockito.mock(RagVersionRepository.class);
        RagVersionFileRepository fileRepository = Mockito.mock(RagVersionFileRepository.class);
        RagVersionDocumentRepository documentRepository = Mockito.mock(RagVersionDocumentRepository.class);
        RagVersionDomainService service = newService(versionRepository, fileRepository, documentRepository);

        when(versionRepository.selectById("version-1")).thenReturn(version("version-1"));
        when(fileRepository.selectList(Mockito.<LambdaQueryWrapper<RagVersionFileEntity>>any()))
                .thenReturn(List.of(file("file-1", "路线.pdf"), file("file-2", "天气.pdf")));
        when(documentRepository.selectList(Mockito.<LambdaQueryWrapper<RagVersionDocumentEntity>>any())).thenReturn(
                List.of(document("doc-1", "file-1"), document("doc-2", "file-2"), document("doc-3", "file-1")));

        RagContentPreviewDTO preview = service.getRagContentPreview("version-1");

        assertEquals(List.of("路线.pdf", "天气.pdf", "路线.pdf"),
                preview.getSampleDocuments().stream().map(item -> item.getFileName()).toList());
        verify(fileRepository, never()).selectById(any());
        verify(fileRepository, never()).selectByIds(any());
    }

    @Test
    void getRagContentPreviewLoadsBrokenFileReferencesInOneBatch() {
        RagVersionRepository versionRepository = Mockito.mock(RagVersionRepository.class);
        RagVersionFileRepository fileRepository = Mockito.mock(RagVersionFileRepository.class);
        RagVersionDocumentRepository documentRepository = Mockito.mock(RagVersionDocumentRepository.class);
        RagVersionDomainService service = newService(versionRepository, fileRepository, documentRepository);

        when(versionRepository.selectById("version-1")).thenReturn(version("version-1"));
        when(fileRepository.selectList(Mockito.<LambdaQueryWrapper<RagVersionFileEntity>>any()))
                .thenReturn(List.of(file("file-1", "路线.pdf")));
        when(documentRepository.selectList(Mockito.<LambdaQueryWrapper<RagVersionDocumentEntity>>any()))
                .thenReturn(List.of(document("doc-1", "missing-file"), document("doc-2", "missing-file")));
        when(fileRepository.selectByIds(List.of("missing-file"))).thenReturn(List.of(file("missing-file", "补充.pdf")));

        RagContentPreviewDTO preview = service.getRagContentPreview("version-1");

        assertEquals(List.of("补充.pdf", "补充.pdf"),
                preview.getSampleDocuments().stream().map(item -> item.getFileName()).toList());
        verify(fileRepository).selectByIds(List.of("missing-file"));
        verify(fileRepository, never()).selectById(any());
    }

    private RagVersionDomainService newService(RagVersionRepository versionRepository,
            RagVersionFileRepository fileRepository, RagVersionDocumentRepository documentRepository) {
        return new RagVersionDomainService(versionRepository, fileRepository, documentRepository,
                Mockito.mock(RagQaDatasetDomainService.class), Mockito.mock(FileDetailRepository.class),
                Mockito.mock(DocumentUnitRepository.class));
    }

    private RagVersionEntity version(String id) {
        RagVersionEntity version = new RagVersionEntity();
        version.setId(id);
        return version;
    }

    private RagVersionFileEntity file(String id, String fileName) {
        RagVersionFileEntity file = new RagVersionFileEntity();
        file.setId(id);
        file.setFileName(fileName);
        return file;
    }

    private RagVersionDocumentEntity document(String id, String fileId) {
        RagVersionDocumentEntity document = new RagVersionDocumentEntity();
        document.setId(id);
        document.setRagVersionFileId(fileId);
        return document;
    }
}
