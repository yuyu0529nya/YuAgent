package org.yu.application.rag.service.manager;

import org.dromara.x.file.storage.core.FileStorageService;
import org.junit.jupiter.api.Test;
import org.yu.application.rag.dto.BatchDeleteFilesRequest;
import org.yu.domain.rag.model.FileDetailEntity;
import org.yu.domain.rag.service.DocumentUnitDomainService;
import org.yu.domain.rag.service.FileDetailDomainService;
import org.yu.infrastructure.exception.BusinessException;
import org.yu.infrastructure.mq.core.MessagePublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileOperationAppServiceTest {

    @Test
    void batchDeleteValidatesEveryFileBeforeDeletingAnyOfThem() {
        FileDetailDomainService fileDetailDomainService = mock(FileDetailDomainService.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        FileDetailEntity ownedFile = new FileDetailEntity();
        ownedFile.setId("file-1");
        ownedFile.setUrl("/owned-file");
        when(fileDetailDomainService.getFileByUrl("/owned-file", "user-1")).thenReturn(ownedFile);
        when(fileDetailDomainService.getFileByUrl("/other-user-file", "user-1"))
                .thenThrow(new BusinessException("文件不存在或无权限访问"));

        FileOperationAppService service = new FileOperationAppService(fileDetailDomainService,
                mock(DocumentUnitDomainService.class), mock(MessagePublisher.class), fileStorageService);
        BatchDeleteFilesRequest request = new BatchDeleteFilesRequest();
        request.setFileUrls(List.of("/owned-file", "/other-user-file"));

        assertThrows(BusinessException.class, () -> service.batchDeleteFiles(request, "user-1"));
        verify(fileStorageService, never()).delete(anyString());
    }
}
