package org.xhy.application.rag.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.xhy.application.rag.service.manager.RagQaDatasetAppService;
import org.xhy.domain.rag.constant.FileProcessingStatusEnum;
import org.xhy.domain.rag.model.FileDetailEntity;
import org.xhy.domain.rag.service.DocumentUnitDomainService;
import org.xhy.domain.rag.service.FileDetailDomainService;

/** 自动恢复卡在已上传状态的文件处理链路 */
@Component
public class FileUploadRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadRecoveryService.class);
    private static final int STALE_SECONDS = 30;
    private static final int BATCH_LIMIT = 20;

    private final FileDetailDomainService fileDetailDomainService;
    private final DocumentUnitDomainService documentUnitDomainService;
    private final RagQaDatasetAppService ragQaDatasetAppService;

    public FileUploadRecoveryService(FileDetailDomainService fileDetailDomainService,
            DocumentUnitDomainService documentUnitDomainService, RagQaDatasetAppService ragQaDatasetAppService) {
        this.fileDetailDomainService = fileDetailDomainService;
        this.documentUnitDomainService = documentUnitDomainService;
        this.ragQaDatasetAppService = ragQaDatasetAppService;
    }

    @Scheduled(initialDelay = 45000, fixedDelay = 30000)
    public void recoverStuckUploads() {
        List<FileDetailEntity> staleFiles = fileDetailDomainService.listStaleUploadedFiles(STALE_SECONDS, BATCH_LIMIT);
        if (staleFiles.isEmpty()) {
            return;
        }

        int recoveredCount = 0;
        for (FileDetailEntity fileEntity : staleFiles) {
            if (!isRecoverable(fileEntity)) {
                continue;
            }

            try {
                log.warn("Recovering stuck uploaded file {}, dataset={}, user={}", fileEntity.getId(),
                        fileEntity.getDataSetId(), fileEntity.getUserId());
                ragQaDatasetAppService.recoverStuckUploadedFile(fileEntity.getId());
                recoveredCount++;
            } catch (Exception e) {
                log.error("Failed to recover stuck uploaded file {}", fileEntity.getId(), e);
            }
        }

        if (recoveredCount > 0) {
            log.warn("Recovered {} stuck uploaded file(s)", recoveredCount);
        }
    }

    private boolean isRecoverable(FileDetailEntity fileEntity) {
        if (!FileProcessingStatusEnum.UPLOADED.getCode().equals(fileEntity.getProcessingStatus())) {
            return false;
        }
        if (fileEntity.getCurrentOcrPageNumber() != null && fileEntity.getCurrentOcrPageNumber() > 0) {
            return false;
        }
        if (fileEntity.getCurrentEmbeddingPageNumber() != null && fileEntity.getCurrentEmbeddingPageNumber() > 0) {
            return false;
        }
        if (fileEntity.getOcrProcessProgress() != null && fileEntity.getOcrProcessProgress() > 0) {
            return false;
        }
        if (fileEntity.getEmbeddingProcessProgress() != null && fileEntity.getEmbeddingProcessProgress() > 0) {
            return false;
        }
        return documentUnitDomainService.listDocumentsByFile(fileEntity.getId()).isEmpty();
    }
}
