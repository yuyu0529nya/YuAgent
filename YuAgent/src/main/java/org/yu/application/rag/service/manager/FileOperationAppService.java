package org.yu.application.rag.service.manager;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yu.infrastructure.mq.core.MessageEnvelope;
import org.yu.infrastructure.mq.core.MessagePublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yu.application.rag.assembler.DocumentUnitAssembler;
import org.yu.application.rag.assembler.FileDetailInfoAssembler;
import org.yu.application.rag.dto.*;
import org.yu.domain.rag.message.RagDocSyncStorageMessage;
import org.yu.domain.rag.model.DocumentUnitEntity;
import org.yu.domain.rag.model.FileDetailEntity;
import org.yu.domain.rag.service.DocumentUnitDomainService;
import org.yu.domain.rag.service.FileDetailDomainService;
import org.yu.infrastructure.mq.enums.EventType;
import org.yu.infrastructure.mq.events.RagDocSyncStorageEvent;
import org.dromara.x.file.storage.core.FileStorageService;

import java.util.List;

/** 文件操作应用服务
 * 
 * @author shilong.zang */
@Service
public class FileOperationAppService {

    private static final Logger log = LoggerFactory.getLogger(FileOperationAppService.class);

    private final FileDetailDomainService fileDetailDomainService;
    private final DocumentUnitDomainService documentUnitDomainService;
    private final MessagePublisher messagePublisher;
    private final FileStorageService fileStorageService;

    public FileOperationAppService(FileDetailDomainService fileDetailDomainService,
            DocumentUnitDomainService documentUnitDomainService, MessagePublisher messagePublisher,
            FileStorageService fileStorageService) {
        this.fileDetailDomainService = fileDetailDomainService;
        this.documentUnitDomainService = documentUnitDomainService;
        this.messagePublisher = messagePublisher;
        this.fileStorageService = fileStorageService;
    }

    /** 根据文件ID获取文件详细信息
     * 
     * @param fileId 文件ID
     * @param userId 用户ID
     * @return 文件详细信息 */
    public FileDetailInfoDTO getFileDetailInfo(String fileId, String userId) {
        FileDetailEntity entity = fileDetailDomainService.getFileById(fileId, userId);
        return FileDetailInfoAssembler.toDTO(entity);
    }

    /** 分页查询文件的语料
     * 
     * @param request 查询请求
     * @param userId 用户ID
     * @return 分页结果 */
    public Page<DocumentUnitDTO> listDocumentUnits(QueryDocumentUnitsRequest request, String userId) {
        IPage<DocumentUnitEntity> entityPage = documentUnitDomainService.listDocumentUnits(request.getFileId(), userId,
                request.getPage(), request.getPageSize(), request.getKeyword());

        Page<DocumentUnitDTO> dtoPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(),
                entityPage.getTotal());

        List<DocumentUnitDTO> dtoList = DocumentUnitAssembler.toDTOs(entityPage.getRecords());
        dtoPage.setRecords(dtoList);
        return dtoPage;
    }

    /** 更新语料内容
     * 
     * @param request 更新请求
     * @param userId 用户ID
     * @return 更新后的语料 */
    @Transactional
    public DocumentUnitDTO updateDocumentUnit(UpdateDocumentUnitRequest request, String userId) {
        // 验证语料是否存在
        DocumentUnitEntity existingEntity = documentUnitDomainService.getDocumentUnit(request.getDocumentUnitId(),
                userId);

        // 转换并更新
        DocumentUnitEntity updateEntity = DocumentUnitAssembler.toEntity(request, userId);
        documentUnitDomainService.updateDocumentUnit(updateEntity, userId);

        // 如果需要重新向量化，发送MQ消息
        if (Boolean.TRUE.equals(request.getReEmbedding())) {
            triggerReEmbedding(existingEntity, request.getContent());
        }

        // 返回更新后的实体
        DocumentUnitEntity updatedEntity = documentUnitDomainService.getDocumentUnit(request.getDocumentUnitId(),
                userId);
        return DocumentUnitAssembler.toDTO(updatedEntity);
    }

    /** 根据语料ID获取单个语料详情
     * 
     * @param documentUnitId 语料ID
     * @param userId 用户ID
     * @return 语料详情 */
    public DocumentUnitDTO getDocumentUnit(String documentUnitId, String userId) {
        DocumentUnitEntity entity = documentUnitDomainService.getDocumentUnit(documentUnitId, userId);
        return DocumentUnitAssembler.toDTO(entity);
    }

    /** 删除语料
     * 
     * @param documentUnitId 语料ID
     * @param userId 用户ID */
    @Transactional
    public void deleteDocumentUnit(String documentUnitId, String userId) {
        documentUnitDomainService.deleteDocumentUnit(documentUnitId, userId);
    }

    /** 批量删除文件
     * 
     * @param request 批量删除请求
     * @param userId 用户ID */
    @Transactional
    public void batchDeleteFiles(BatchDeleteFilesRequest request, String userId) {
        List<FileDetailEntity> files = request.getFileUrls().stream()
                .map(fileUrl -> fileDetailDomainService.getFileByUrl(fileUrl, userId)).toList();
        for (FileDetailEntity file : files) {
            try {
                fileStorageService.delete(file.getUrl());
            } catch (Exception e) {
                log.warn("删除文件失败: {}", file.getId(), e);
            }
        }
    }

    /** 触发重新向量化
     * 
     * @param documentUnit 文档单元
     * @param newContent 新内容 */
    private void triggerReEmbedding(DocumentUnitEntity documentUnit, String newContent) {
        try {
            // 获取文件信息
            FileDetailEntity fileEntity = fileDetailDomainService.getFileByIdWithoutUserCheck(documentUnit.getFileId());

            // 构建向量化消息
            RagDocSyncStorageMessage storageMessage = new RagDocSyncStorageMessage();
            storageMessage.setId(documentUnit.getId());
            storageMessage.setFileId(documentUnit.getFileId());
            storageMessage.setFileName(fileEntity.getOriginalFilename());
            storageMessage.setPage(documentUnit.getPage());
            storageMessage.setContent(newContent);
            storageMessage.setVector(true);
            storageMessage.setDatasetId(fileEntity.getDataSetId());

            // 发送消息
            MessageEnvelope<RagDocSyncStorageMessage> envelope = MessageEnvelope.builder(storageMessage)
                    .addEventType(EventType.DOC_SYNC_RAG).description("语料内容修改后重新向量化 - 页面 " + documentUnit.getPage())
                    .build();
            messagePublisher.publish(RagDocSyncStorageEvent.route(), envelope);

        } catch (Exception e) {
            log.warn("触发重新向量化失败: documentUnitId={}", documentUnit.getId(), e);
        }
    }
}
