package org.yu.domain.llm.event;

/** 模型删除事件
 * 
 * @author yu
 * @since 1.0.0 */
public class ModelDeletedEvent extends ModelDomainEvent {

    public ModelDeletedEvent(String modelId, String userId) {
        super(modelId, userId);
    }
}