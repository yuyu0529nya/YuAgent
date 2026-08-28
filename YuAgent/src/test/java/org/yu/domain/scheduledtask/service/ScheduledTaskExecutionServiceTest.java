package org.yu.domain.scheduledtask.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.yu.domain.scheduledtask.constant.RepeatType;
import org.yu.domain.scheduledtask.constant.ScheduleTaskStatus;
import org.yu.domain.scheduledtask.model.RepeatConfig;
import org.yu.domain.scheduledtask.model.ScheduledTaskEntity;

class ScheduledTaskExecutionServiceTest {

    @Test
    void shouldRescheduleExpiredRecurringTaskOnStartup() {
        ScheduledTaskDomainService domainService = mock(ScheduledTaskDomainService.class);
        TaskScheduleService scheduleService = mock(TaskScheduleService.class);
        DelayedTaskQueueManager queueManager = mock(DelayedTaskQueueManager.class);
        ScheduledTaskExecutionService service = new ScheduledTaskExecutionService(domainService, scheduleService,
                queueManager);
        ScheduledTaskEntity task = activeTask();
        LocalDateTime nextTime = LocalDateTime.now().plusDays(1);
        task.setNextExecuteTime(LocalDateTime.now().minusDays(1));

        when(domainService.getActiveTasksToExecute()).thenReturn(List.of(task));
        when(scheduleService.shouldExecuteAt(any(ScheduledTaskEntity.class), any(LocalDateTime.class)))
                .thenReturn(false);
        when(scheduleService.calculateNextExecuteTime(any(ScheduledTaskEntity.class), any(LocalDateTime.class)))
                .thenReturn(nextTime);

        service.init();

        assertEquals(nextTime, task.getNextExecuteTime());
        verify(domainService).updateTask(task);
        verify(queueManager).addTask(task, nextTime);
    }

    @Test
    void shouldCompleteExpiredOneTimeTaskOnStartup() {
        ScheduledTaskDomainService domainService = mock(ScheduledTaskDomainService.class);
        TaskScheduleService scheduleService = mock(TaskScheduleService.class);
        DelayedTaskQueueManager queueManager = mock(DelayedTaskQueueManager.class);
        ScheduledTaskExecutionService service = new ScheduledTaskExecutionService(domainService, scheduleService,
                queueManager);
        ScheduledTaskEntity task = activeTask();
        task.setRepeatType(RepeatType.NONE);
        task.setNextExecuteTime(LocalDateTime.now().minusDays(1));

        when(domainService.getActiveTasksToExecute()).thenReturn(List.of(task));
        when(scheduleService.shouldExecuteAt(any(ScheduledTaskEntity.class), any(LocalDateTime.class)))
                .thenReturn(false);
        when(scheduleService.calculateNextExecuteTime(any(ScheduledTaskEntity.class), any(LocalDateTime.class)))
                .thenReturn(null);

        service.init();

        assertEquals(ScheduleTaskStatus.COMPLETED, task.getStatus());
        verify(domainService).completeTask(task.getId(), task.getUserId());
        verify(queueManager, never()).addTask(any(ScheduledTaskEntity.class), any(LocalDateTime.class));
    }

    private ScheduledTaskEntity activeTask() {
        ScheduledTaskEntity task = new ScheduledTaskEntity();
        task.setId("task-id");
        task.setUserId("user-id");
        task.setStatus(ScheduleTaskStatus.ACTIVE);
        task.setRepeatType(RepeatType.DAILY);
        task.setRepeatConfig(new RepeatConfig(LocalDateTime.now().minusDays(1)));
        return task;
    }
}
