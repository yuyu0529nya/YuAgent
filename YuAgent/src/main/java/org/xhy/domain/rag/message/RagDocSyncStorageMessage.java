package org.xhy.domain.rag.message;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import org.xhy.domain.rag.model.ModelConfig;

/** Message payload for document vectorization tasks. */
public class RagDocSyncStorageMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = -5764144581856293209L;

    private String id;

    private String fileId;

    private Integer page;

    private String content;

    private Boolean isVector;

    private String fileName;

    private String datasetId;

    private String userId;

    private ModelConfig embeddingModelConfig;

    /** Batch vectorization tasks aggregated for the same file. */
    private List<BatchUnit> batchUnits;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Boolean getVector() {
        return isVector;
    }

    public void setVector(Boolean vector) {
        isVector = vector;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public ModelConfig getEmbeddingModelConfig() {
        return embeddingModelConfig;
    }

    public void setEmbeddingModelConfig(ModelConfig embeddingModelConfig) {
        this.embeddingModelConfig = embeddingModelConfig;
    }

    public List<BatchUnit> getBatchUnits() {
        return batchUnits;
    }

    public void setBatchUnits(List<BatchUnit> batchUnits) {
        this.batchUnits = batchUnits;
    }

    public boolean isBatchMessage() {
        return batchUnits != null && !batchUnits.isEmpty();
    }

    public static class BatchUnit implements Serializable {

        @Serial
        private static final long serialVersionUID = 6416749823426796165L;

        private String id;

        private Integer page;

        private String content;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Integer getPage() {
            return page;
        }

        public void setPage(Integer page) {
            this.page = page;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
