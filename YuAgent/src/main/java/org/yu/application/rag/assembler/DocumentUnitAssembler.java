package org.yu.application.rag.assembler;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.BeanUtils;
import org.yu.application.rag.dto.DocumentUnitDTO;
import org.yu.application.rag.dto.UpdateDocumentUnitRequest;
import org.yu.domain.rag.model.DocumentUnitEntity;

/** Document unit DTO assembler. */
public class DocumentUnitAssembler {

    public static DocumentUnitDTO toDTO(DocumentUnitEntity entity) {
        return toDTO(entity, null);
    }

    public static DocumentUnitDTO toDTO(DocumentUnitEntity entity, String fileName) {
        if (entity == null) {
            return null;
        }

        DocumentUnitDTO dto = new DocumentUnitDTO();
        BeanUtils.copyProperties(entity, dto);
        dto.setFileName(fileName);

        if (entity.getCreatedAt() != null) {
            dto.setCreatedAt(entity.getCreatedAt().toString());
        }
        if (entity.getUpdatedAt() != null) {
            dto.setUpdatedAt(entity.getUpdatedAt().toString());
        }

        return dto;
    }

    public static DocumentUnitEntity toEntity(UpdateDocumentUnitRequest request, String userId) {
        if (request == null) {
            return null;
        }

        DocumentUnitEntity entity = new DocumentUnitEntity();
        entity.setId(request.getDocumentUnitId());
        entity.setContent(request.getContent());
        if (Boolean.TRUE.equals(request.getReEmbedding())) {
            entity.setIsVector(false);
        }
        return entity;
    }

    public static List<DocumentUnitDTO> toDTOs(List<DocumentUnitEntity> entities) {
        return toDTOs(entities, Collections.emptyMap());
    }

    public static List<DocumentUnitDTO> toDTOs(List<DocumentUnitEntity> entities, Map<String, String> fileNames) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }

        return entities.stream().map(entity -> toDTO(entity, fileNames.get(entity.getFileId())))
                .collect(Collectors.toList());
    }
}
