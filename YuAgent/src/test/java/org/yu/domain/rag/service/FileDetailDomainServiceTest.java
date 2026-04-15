package org.yu.domain.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Map;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.upload.UploadPretreatment;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.yu.domain.rag.model.FileDetailEntity;
import org.yu.domain.rag.repository.FileDetailRepository;

class FileDetailDomainServiceTest {

    @Test
    void uploadRemoteFileToDataset_shouldUploadViaTempFile() {
        FileStorageService fileStorageService = mock(FileStorageService.class);
        FileDetailRepository repository = mock(FileDetailRepository.class);
        FileProcessingStateMachineService stateMachineService = mock(FileProcessingStateMachineService.class);
        UploadPretreatment pretreatment = mock(UploadPretreatment.class);
        FileInfo fileInfo = mock(FileInfo.class);

        when(fileStorageService.of(any(), anyString(), anyString(), anyLong())).thenReturn(pretreatment);
        when(pretreatment.setMetadata(any())).thenReturn(pretreatment);
        when(pretreatment.upload()).thenReturn(fileInfo);
        when(fileInfo.getId()).thenReturn("file-1");
        when(fileInfo.getUrl()).thenReturn("https://example.com/file.pdf");
        when(fileInfo.getSize()).thenReturn(123L);
        when(fileInfo.getFilename()).thenReturn("storage.pdf");
        when(fileInfo.getOriginalFilename()).thenReturn("paper.pdf");
        when(fileInfo.getPath()).thenReturn("s3/");
        when(fileInfo.getExt()).thenReturn("pdf");
        when(fileInfo.getContentType()).thenReturn("application/pdf");
        when(fileInfo.getPlatform()).thenReturn("amazon-s3-1");

        FileDetailDomainService service = new FileDetailDomainService(fileStorageService, repository,
                stateMachineService);

        FileDetailEntity entity = new FileDetailEntity();
        entity.setDataSetId("dataset-1");
        entity.setUserId("user-1");

        FileDetailEntity result = service.uploadRemoteFileToDataset(entity, new byte[] {1, 2, 3}, "paper.pdf",
                "application/pdf");

        ArgumentCaptor<Object> objectCaptor = ArgumentCaptor.forClass(Object.class);
        verify(fileStorageService).of(objectCaptor.capture(), anyString(), anyString(), anyLong());
        verify(pretreatment).setMetadata(Map.of("dataset", "dataset-1", "userid", "user-1"));
        verify(stateMachineService).processFileState(entity);

        Object uploadSource = objectCaptor.getValue();
        assertTrue(uploadSource instanceof File);
        assertNotNull(result);
        assertEquals("file-1", result.getId());
        assertEquals("paper.pdf", result.getOriginalFilename());
        assertEquals("application/pdf", result.getContentType());
    }
}
