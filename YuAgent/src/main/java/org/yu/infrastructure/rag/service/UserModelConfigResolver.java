package org.yu.infrastructure.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.yu.application.user.dto.UserSettingsDTO;
import org.yu.application.user.service.UserSettingsAppService;
import org.yu.domain.llm.model.ModelEntity;
import org.yu.domain.llm.model.ProviderEntity;
import org.yu.domain.llm.service.LLMDomainService;
import org.yu.domain.rag.model.ModelConfig;
import org.yu.infrastructure.exception.BusinessException;

/** Resolves user model configs used by the document pipeline. */
@Service
public class UserModelConfigResolver {

    private static final Logger log = LoggerFactory.getLogger(UserModelConfigResolver.class);

    private final UserSettingsAppService userSettingsAppService;
    private final LLMDomainService llmDomainService;

    public UserModelConfigResolver(UserSettingsAppService userSettingsAppService, LLMDomainService llmDomainService) {
        this.userSettingsAppService = userSettingsAppService;
        this.llmDomainService = llmDomainService;
    }

    public ModelConfig getUserEmbeddingModelConfig(String userId) {
        try {
            UserSettingsDTO userSettingsDTO = userSettingsAppService.getUserSettings(userId);
            if (userSettingsDTO == null || userSettingsDTO.getSettingConfig() == null
                    || userSettingsDTO.getSettingConfig().getDefaultEmbeddingModel() == null) {
                String errorMsg = String.format("User %s has no default embedding model configured", userId);
                log.error(errorMsg);
                throw new BusinessException(errorMsg);
            }

            String modelId = userSettingsDTO.getSettingConfig().getDefaultEmbeddingModel();
            log.info("Resolved embedding model config for user {}, modelId={}", userId, modelId);
            return getModelConfigFromDatabase(modelId, userId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("Failed to resolve embedding model config for user %s: %s", userId,
                    e.getMessage());
            log.error(errorMsg, e);
            throw new BusinessException(errorMsg, e);
        }
    }

    public ModelConfig getUserChatModelConfig(String userId) {
        try {
            UserSettingsDTO userSettingsDTO = userSettingsAppService.getUserSettings(userId);
            if (userSettingsDTO == null || userSettingsDTO.getSettingConfig() == null
                    || userSettingsDTO.getSettingConfig().getDefaultModel() == null) {
                String errorMsg = String.format("User %s has no default chat model configured", userId);
                log.error(errorMsg);
                throw new BusinessException(errorMsg);
            }

            String modelId = userSettingsDTO.getSettingConfig().getDefaultModel();
            log.info("Resolved chat model config for user {}, modelId={}", userId, modelId);
            return getModelConfigFromDatabase(modelId, userId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("Failed to resolve chat model config for user %s: %s", userId,
                    e.getMessage());
            log.error(errorMsg, e);
            throw new BusinessException(errorMsg, e);
        }
    }

    public ModelConfig getUserOcrModelConfig(String userId) {
        try {
            UserSettingsDTO userSettingsDTO = userSettingsAppService.getUserSettings(userId);
            if (userSettingsDTO == null || userSettingsDTO.getSettingConfig() == null
                    || userSettingsDTO.getSettingConfig().getDefaultOcrModel() == null) {
                log.warn("User {} has no dedicated OCR model configured, falling back to chat model", userId);
                return getUserChatModelConfig(userId);
            }

            String modelId = userSettingsDTO.getSettingConfig().getDefaultOcrModel();
            log.info("Resolved OCR model config for user {}, modelId={}", userId, modelId);
            return getModelConfigFromDatabase(modelId, userId);
        } catch (BusinessException e) {
            log.warn("Failed to resolve OCR model for user {}, falling back to chat model: {}", userId, e.getMessage());
            return getUserChatModelConfig(userId);
        } catch (Exception e) {
            log.warn("Unexpected OCR model lookup failure for user {}, falling back to chat model: {}", userId,
                    e.getMessage());
            return getUserChatModelConfig(userId);
        }
    }

    private ModelConfig getModelConfigFromDatabase(String modelId, String userId) {
        try {
            ModelEntity modelEntity = llmDomainService.findModelById(modelId);
            if (modelEntity == null) {
                String errorMsg = String.format("Configured model %s for user %s does not exist", modelId, userId);
                log.error(errorMsg);
                throw new BusinessException(errorMsg);
            }

            if (!modelEntity.getStatus()) {
                String errorMsg = String.format("Configured model %s for user %s is disabled", modelId, userId);
                log.error(errorMsg);
                throw new BusinessException(errorMsg);
            }

            ProviderEntity providerEntity = llmDomainService.getProvider(modelEntity.getProviderId());
            if (!providerEntity.getStatus()) {
                String errorMsg = String.format("Provider for model %s used by user %s is disabled", modelId, userId);
                log.error(errorMsg);
                throw new BusinessException(errorMsg);
            }

            providerEntity.isAvailable(providerEntity.getUserId());

            ModelConfig modelConfig = new ModelConfig(providerEntity.getConfig().getApiKey(),
                    providerEntity.getConfig().getBaseUrl(), modelEntity.getType(), providerEntity.getProtocol(),
                    modelEntity.getModelEndpoint());

            log.info("Loaded model config for user {}, modelId={}, baseUrl={}", userId, modelEntity.getModelId(),
                    providerEntity.getConfig().getBaseUrl());
            return modelConfig;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("Failed to load model %s for user %s: %s", modelId, userId, e.getMessage());
            log.error(errorMsg, e);
            throw new BusinessException(errorMsg, e);
        }
    }
}
