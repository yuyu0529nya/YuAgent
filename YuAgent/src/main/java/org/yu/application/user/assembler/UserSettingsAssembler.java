package org.yu.application.user.assembler;

import org.springframework.beans.BeanUtils;
import org.yu.application.user.dto.UserSettingsDTO;
import org.yu.domain.user.model.UserSettingsEntity;
import org.yu.interfaces.dto.user.request.UserSettingsUpdateRequest;

/** 用户设置转换器 */
public class UserSettingsAssembler {

    /** 实体转DTO */
    public static UserSettingsDTO toDTO(UserSettingsEntity entity) {
        if (entity == null) {
            return null;
        }
        UserSettingsDTO dto = new UserSettingsDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    /** 请求转实体 */
    public static UserSettingsEntity toEntity(UserSettingsUpdateRequest request, String userId) {
        if (request == null) {
            return null;
        }
        UserSettingsEntity entity = new UserSettingsEntity();
        entity.setSettingConfig(request.getSettingConfig());
        entity.setUserId(userId);
        return entity;
    }
}