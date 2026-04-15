package org.yu.application.rag.dto;

import org.yu.domain.rag.constant.FileProcessingStatusEnum;

/** File processing progress response. */
public class FileProcessProgressDTO {

    private String fileId;
    private String filename;
    private FileProcessingStatusEnum processingStatusEnum;
    private Integer processingStatus;
    private String processingStatusDescription;
    private String processingStage;
    private String processingStageDescription;
    private Integer currentOcrPageNumber;
    private Integer currentEmbeddingPageNumber;
    private Integer filePageSize;
    private Double ocrProcessProgress;
    private Double embeddingProcessProgress;
    private String statusDescription;

    @Deprecated
    private Integer isInitialize;

    @Deprecated
    private Integer isEmbedding;

    @Deprecated
    private String initializeStatus;

    @Deprecated
    private String embeddingStatus;

    private Integer currentPageNumber;
    private Double processProgress;

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public FileProcessingStatusEnum getProcessingStatusEnum() {
        return processingStatusEnum;
    }

    public void setProcessingStatusEnum(FileProcessingStatusEnum processingStatusEnum) {
        this.processingStatusEnum = processingStatusEnum;
    }

    public Integer getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(Integer processingStatus) {
        this.processingStatus = processingStatus;
    }

    public String getProcessingStatusDescription() {
        return processingStatusDescription;
    }

    public void setProcessingStatusDescription(String processingStatusDescription) {
        this.processingStatusDescription = processingStatusDescription;
    }

    public String getProcessingStage() {
        return processingStage;
    }

    public void setProcessingStage(String processingStage) {
        this.processingStage = processingStage;
    }

    public String getProcessingStageDescription() {
        return processingStageDescription;
    }

    public void setProcessingStageDescription(String processingStageDescription) {
        this.processingStageDescription = processingStageDescription;
    }

    public Integer getCurrentOcrPageNumber() {
        return currentOcrPageNumber;
    }

    public void setCurrentOcrPageNumber(Integer currentOcrPageNumber) {
        this.currentOcrPageNumber = currentOcrPageNumber;
    }

    public Integer getCurrentEmbeddingPageNumber() {
        return currentEmbeddingPageNumber;
    }

    public void setCurrentEmbeddingPageNumber(Integer currentEmbeddingPageNumber) {
        this.currentEmbeddingPageNumber = currentEmbeddingPageNumber;
    }

    public Integer getFilePageSize() {
        return filePageSize;
    }

    public void setFilePageSize(Integer filePageSize) {
        this.filePageSize = filePageSize;
    }

    public Double getOcrProcessProgress() {
        return ocrProcessProgress;
    }

    public void setOcrProcessProgress(Double ocrProcessProgress) {
        this.ocrProcessProgress = ocrProcessProgress;
    }

    public Double getEmbeddingProcessProgress() {
        return embeddingProcessProgress;
    }

    public void setEmbeddingProcessProgress(Double embeddingProcessProgress) {
        this.embeddingProcessProgress = embeddingProcessProgress;
    }

    public String getStatusDescription() {
        return statusDescription;
    }

    public void setStatusDescription(String statusDescription) {
        this.statusDescription = statusDescription;
    }

    @Deprecated
    public Integer getIsInitialize() {
        return isInitialize;
    }

    @Deprecated
    public void setIsInitialize(Integer isInitialize) {
        this.isInitialize = isInitialize;
    }

    @Deprecated
    public Integer getIsEmbedding() {
        return isEmbedding;
    }

    @Deprecated
    public void setIsEmbedding(Integer isEmbedding) {
        this.isEmbedding = isEmbedding;
    }

    @Deprecated
    public String getInitializeStatus() {
        return initializeStatus;
    }

    @Deprecated
    public void setInitializeStatus(String initializeStatus) {
        this.initializeStatus = initializeStatus;
    }

    @Deprecated
    public String getEmbeddingStatus() {
        return embeddingStatus;
    }

    @Deprecated
    public void setEmbeddingStatus(String embeddingStatus) {
        this.embeddingStatus = embeddingStatus;
    }

    public Integer getCurrentPageNumber() {
        return currentPageNumber;
    }

    public void setCurrentPageNumber(Integer currentPageNumber) {
        this.currentPageNumber = currentPageNumber;
    }

    public Double getProcessProgress() {
        return processProgress;
    }

    public void setProcessProgress(Double processProgress) {
        this.processProgress = processProgress;
    }
}
