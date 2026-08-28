package org.yu.application.rag.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;
import org.yu.application.rag.service.manager.RagQaDatasetAppService;
import org.yu.domain.rag.constant.FileProcessingStatusEnum;
import org.yu.domain.rag.model.FileDetailEntity;
import org.yu.domain.rag.service.DocumentUnitDomainService;
import org.yu.domain.rag.service.FileDetailDomainService;
import org.yu.infrastructure.utils.JsonUtils;

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
                markRecoveryBlocked(fileEntity, e);
            }
        }

        if (recoveredCount > 0) {
            log.warn("Recovered {} stuck uploaded file(s)", recoveredCount);
        }
    }

    private boolean isRecoverable(FileDetailEntity fileEntity) {
        boolean uploaded = FileProcessingStatusEnum.UPLOADED.getCode().equals(fileEntity.getProcessingStatus());
        boolean zombieOcrProcessing = FileProcessingStatusEnum.OCR_PROCESSING.getCode()
                .equals(fileEntity.getProcessingStatus());
        if (!uploaded && !zombieOcrProcessing) {
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
        if (hasActiveProcessingStage(fileEntity)) {
            return false;
        }
        return documentUnitDomainService.listDocumentsByFile(fileEntity.getId()).isEmpty();
    }

    private boolean hasActiveProcessingStage(FileDetailEntity fileEntity) {
        Map<String, Object> attrMap = JsonUtils.parseMap(fileEntity.getAttr());
        if (attrMap == null || attrMap.isEmpty()) {
            return false;
        }
        Object processingStage = attrMap.get("processingStage");
        if (processingStage != null && StringUtils.hasText(String.valueOf(processingStage))) {
            return true;
        }
        Object recoveryBlockedReason = attrMap.get("recoveryBlockedReason");
        return recoveryBlockedReason != null && StringUtils.hasText(String.valueOf(recoveryBlockedReason));
    }

    private void markRecoveryBlocked(FileDetailEntity fileEntity, Exception e) {
        FileDetailEntity latestFile = fileDetailDomainService.getFileById(fileEntity.getId());
        Map<String, Object> attrMap = JsonUtils.parseMap(latestFile.getAttr());
        if (attrMap == null) {
            attrMap = new java.util.HashMap<>();
        }
        attrMap.put("processingStage", "RECOVERY_BLOCKED");
        attrMap.put("processingStageDescription", "自动恢复已停止，请先补齐默认模型配置后再手动重试");
        attrMap.put("recoveryBlockedReason", e.getMessage());

        latestFile.setAttr(JsonUtils.toJsonString(attrMap));
        latestFile.setProcessingStatus(FileProcessingStatusEnum.OCR_FAILED.getCode());
        fileDetailDomainService.updateFile(latestFile);
    }
}
