package org.yu.application.task.service;

import org.springframework.stereotype.Service;
import org.yu.application.task.assembler.TaskAssembler;
import org.yu.application.task.dto.TaskDTO;
import org.yu.domain.task.model.TaskAggregate;
import org.yu.domain.task.model.TaskEntity;
import org.yu.domain.task.service.TaskDomainService;

import java.util.ArrayList;
import java.util.List;

/** 任务应用服务 */
@Service
public class TaskAppService {

    private final TaskDomainService taskDomainService;

    public TaskAppService(TaskDomainService taskDomainService) {
        this.taskDomainService = taskDomainService;
    }

    /** 获取当前会话的最新任务
     *
     * @param sessionId 会话ID
     * @return 任务DTO列表 */
    public TaskAggregate getCurrentSessionTask(String sessionId, String userId) {
        return taskDomainService.getCurrentSessionTask(sessionId, userId);

    }
}