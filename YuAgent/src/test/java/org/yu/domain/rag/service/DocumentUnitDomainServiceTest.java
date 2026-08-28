package org.yu.domain.rag.service;

import org.junit.jupiter.api.Test;
import org.yu.domain.rag.model.DocumentUnitEntity;
import org.yu.domain.rag.model.FileDetailEntity;
import org.yu.domain.rag.repository.DocumentUnitRepository;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentUnitDomainServiceTest {

    @Test
    void getDocumentUnitChecksOwnershipThroughItsFile() {
        DocumentUnitRepository documentUnitRepository = mock(DocumentUnitRepository.class);
        FileDetailDomainService fileDetailDomainService = mock(FileDetailDomainService.class);
        DocumentUnitEntity unit = new DocumentUnitEntity();
        unit.setId("unit-1");
        unit.setFileId("file-1");
        when(documentUnitRepository.selectById("unit-1")).thenReturn(unit);
        when(fileDetailDomainService.getFileById("file-1", "user-1")).thenReturn(new FileDetailEntity());

        DocumentUnitDomainService service = new DocumentUnitDomainService(documentUnitRepository,
                fileDetailDomainService);

        assertSame(unit, service.getDocumentUnit("unit-1", "user-1"));
        verify(fileDetailDomainService).getFileById("file-1", "user-1");
    }
}
