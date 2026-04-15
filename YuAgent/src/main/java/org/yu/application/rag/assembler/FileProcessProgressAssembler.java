package org.yu.application.rag.assembler;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;
import org.yu.application.rag.dto.FileProcessProgressDTO;
import org.yu.domain.rag.constant.FileProcessingStatusEnum;
import org.yu.domain.rag.model.FileDetailEntity;
import org.yu.infrastructure.utils.JsonUtils;

/** Converts file processing progress entities to DTOs. */
public class FileProcessProgressAssembler {

    private static final String ATTR_PROCESSING_STAGE = "processingStage";
    private static final String ATTR_PROCESSING_STAGE_DESCRIPTION = "processingStageDescription";

    public static FileProcessProgressDTO toDTO(FileDetailEntity entity) {
        if (entity == null) {
            return null;
        }

        FileProcessProgressDTO dto = new FileProcessProgressDTO();
        dto.setFileId(entity.getId());
        dto.setFilename(entity.getOriginalFilename());

        Integer processingStatus = entity.getProcessingStatus();
        if (processingStatus != null) {
            FileProcessingStatusEnum statusEnum = FileProcessingStatusEnum.fromCode(processingStatus);
            dto.setProcessingStatusEnum(statusEnum);
            dto.setProcessingStatus(processingStatus);
            dto.setProcessingStatusDescription(statusEnum.getDescription());
        }

        Map<String, Object> attrMap = JsonUtils.parseMap(entity.getAttr());
        if (attrMap != null) {
            dto.setProcessingStage(stringValue(attrMap.get(ATTR_PROCESSING_STAGE)));
            dto.setProcessingStageDescription(stringValue(attrMap.get(ATTR_PROCESSING_STAGE_DESCRIPTION)));
        }

        dto.setCurrentOcrPageNumber(entity.getCurrentOcrPageNumber() != null ? entity.getCurrentOcrPageNumber() : 0);
        dto.setCurrentEmbeddingPageNumber(
                entity.getCurrentEmbeddingPageNumber() != null ? entity.getCurrentEmbeddingPageNumber() : 0);
        dto.setFilePageSize(entity.getFilePageSize() != null ? entity.getFilePageSize() : 0);
        dto.setOcrProcessProgress(entity.getOcrProcessProgress() != null ? entity.getOcrProcessProgress() : 0.0);
        dto.setEmbeddingProcessProgress(
                entity.getEmbeddingProcessProgress() != null ? entity.getEmbeddingProcessProgress() : 0.0);

        mapToLegacyStatus(dto, processingStatus);

        dto.setCurrentPageNumber(dto.getCurrentOcrPageNumber());
        dto.setProcessProgress(dto.getOcrProcessProgress());
        dto.setStatusDescription(getStatusDescription(dto));
        return dto;
    }

    public static List<FileProcessProgressDTO> toDTOs(List<FileDetailEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        return entities.stream().map(FileProcessProgressAssembler::toDTO).collect(Collectors.toList());
    }

    private static void mapToLegacyStatus(FileProcessProgressDTO dto, Integer processingStatus) {
        if (processingStatus == null) {
            dto.setIsInitialize(0);
            dto.setIsEmbedding(0);
            dto.setInitializeStatus("待初始化");
            dto.setEmbeddingStatus("待向量化");
            return;
        }

        FileProcessingStatusEnum statusEnum = FileProcessingStatusEnum.fromCode(processingStatus);
        switch (statusEnum) {
            case UPLOADED:
                dto.setIsInitialize(0);
                dto.setIsEmbedding(0);
                dto.setInitializeStatus("待初始化");
                dto.setEmbeddingStatus("待向量化");
                break;
            case OCR_PROCESSING:
                dto.setIsInitialize(1);
                dto.setIsEmbedding(0);
                dto.setInitializeStatus("初始化中");
                dto.setEmbeddingStatus("待向量化");
                break;
            case OCR_COMPLETED:
                dto.setIsInitialize(2);
                dto.setIsEmbedding(0);
                dto.setInitializeStatus("初始化完成");
                dto.setEmbeddingStatus("待向量化");
                break;
            case EMBEDDING_PROCESSING:
                dto.setIsInitialize(2);
                dto.setIsEmbedding(1);
                dto.setInitializeStatus("初始化完成");
                dto.setEmbeddingStatus("向量化中");
                break;
            case COMPLETED:
                dto.setIsInitialize(2);
                dto.setIsEmbedding(2);
                dto.setInitializeStatus("初始化完成");
                dto.setEmbeddingStatus("向量化完成");
                break;
            case OCR_FAILED:
                dto.setIsInitialize(3);
                dto.setIsEmbedding(0);
                dto.setInitializeStatus("初始化失败");
                dto.setEmbeddingStatus("待向量化");
                break;
            case EMBEDDING_FAILED:
                dto.setIsInitialize(2);
                dto.setIsEmbedding(3);
                dto.setInitializeStatus("初始化完成");
                dto.setEmbeddingStatus("向量化失败");
                break;
            default:
                dto.setIsInitialize(0);
                dto.setIsEmbedding(0);
                dto.setInitializeStatus("未知状态");
                dto.setEmbeddingStatus("未知状态");
                break;
        }
    }

    private static String getStatusDescription(FileProcessProgressDTO dto) {
        if (dto.getProcessingStatusEnum() == FileProcessingStatusEnum.OCR_PROCESSING
                && StringUtils.hasText(dto.getProcessingStageDescription())) {
            return dto.getProcessingStageDescription();
        }
        if (StringUtils.hasText(dto.getProcessingStatusDescription())) {
            return dto.getProcessingStatusDescription();
        }
        return "待初始化";
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
