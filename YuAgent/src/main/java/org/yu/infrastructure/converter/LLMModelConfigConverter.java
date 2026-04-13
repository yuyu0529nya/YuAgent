package org.yu.infrastructure.converter;

import org.apache.ibatis.type.MappedTypes;
import org.yu.domain.agent.model.LLMModelConfig;

/** LLMModelConfig JSON转换器 */
@MappedTypes(LLMModelConfig.class)
public class LLMModelConfigConverter extends JsonToStringConverter<LLMModelConfig> {

    public LLMModelConfigConverter() {
        super(LLMModelConfig.class);
    }
}