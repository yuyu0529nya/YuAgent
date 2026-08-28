package org.yu.domain.rag.constant;

/** Unified file processing status. */
public enum FileProcessingStatusEnum {

    UPLOADED(0, "已上传"), OCR_PROCESSING(1, "解析中"), OCR_COMPLETED(2, "解析完成"), EMBEDDING_PROCESSING(3,
            "向量化中"), COMPLETED(4, "处理完成"), OCR_FAILED(5, "解析失败"), EMBEDDING_FAILED(6, "向量化失败");

    private final Integer code;
    private final String description;

    FileProcessingStatusEnum(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static FileProcessingStatusEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (FileProcessingStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown file processing status code: " + code);
    }

    public boolean isProcessing() {
        return this == OCR_PROCESSING || this == EMBEDDING_PROCESSING;
    }

    public boolean isFailed() {
        return this == OCR_FAILED || this == EMBEDDING_FAILED;
    }

    public boolean isCompleted() {
        return this == COMPLETED;
    }

    public boolean canStartOcr() {
        return this == UPLOADED;
    }

    public boolean canStartEmbedding() {
        return this == OCR_COMPLETED;
    }
}
