package org.yu.application.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ImportFileByUrlRequest {

    @NotBlank(message = "鏁版嵁闆咺D涓嶈兘涓虹┖")
    private String datasetId;

    @NotBlank(message = "鏂囦欢閾炬帴涓嶈兘涓虹┖")
    @Size(max = 2000, message = "鏂囦欢閾炬帴杩囬暱")
    private String url;

    @Size(max = 255, message = "鏂囦欢鍚嶈繃闀?")
    private String filename;

    public String getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }
}
