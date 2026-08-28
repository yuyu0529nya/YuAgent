package org.yu.domain.scheduledtask.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.yu.domain.scheduledtask.constant.RepeatType;
import org.yu.domain.scheduledtask.constant.ScheduleTaskStatus;
import org.yu.domain.scheduledtask.model.RepeatConfig;
import org.yu.domain.scheduledtask.model.ScheduledTaskEntity;

class ScheduleTaskExecutorTest {

    @Test
    void shouldSkipQueuedTaskThatWasPausedAfterItWasEnqueued() {
        ScheduledTaskDomainService domainService = mock(ScheduledTaskDomainService.class);
        ScheduleTaskExecutor executor = new ScheduleTaskExecutor(mock(ApplicationEventPublisher.class), domainService,
                new TaskScheduleService());
        ScheduledTaskEntity queuedTask = activeTask();
        ScheduledTaskEntity pausedTask = activeTask();
        pausedTask.setStatus(ScheduleTaskStatus.PAUSED);
        when(domainService.getTask(queuedTask.getId(), queuedTask.getUserId())).thenReturn(pausedTask);

        assertNull(executor.getExecutableTask(queuedTask));
    }

    @Test
    void shouldReportSuccessfulExecutionOnlyAfterTaskStateIsPersisted() {
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        ScheduledTaskDomainService domainService = mock(ScheduledTaskDomainService.class);
        TaskScheduleService scheduleService = mock(TaskScheduleService.class);
        ScheduleTaskExecutor executor = new ScheduleTaskExecutor(eventPublisher, domainService, scheduleService);
        ScheduledTaskEntity task = activeTask();
        task.setRepeatType(RepeatType.NONE);
        when(scheduleService.calculateNextExecuteTime(any(ScheduledTaskEntity.class), any(LocalDateTime.class)))
                .thenReturn(null);

        assertTrue(executor.executeTask(task));
        verify(domainService).recordExecution(any(String.class), any(LocalDateTime.class));
        verify(domainService).completeTask(task.getId(), task.getUserId());
    }

    @Test
    void shouldNotReportSuccessWhenTaskPersistenceFails() {
        ScheduledTaskDomainService domainService = mock(ScheduledTaskDomainService.class);
        ScheduleTaskExecutor executor = new ScheduleTaskExecutor(mock(ApplicationEventPublisher.class), domainService,
                new TaskScheduleService());
        ScheduledTaskEntity task = activeTask();
        task.setRepeatType(RepeatType.NONE);
        org.mockito.Mockito.doThrow(new RuntimeException("database unavailable")).when(domainService)
                .recordExecution(any(String.class), any(LocalDateTime.class));

        assertFalse(executor.executeTask(task));
    }

    private ScheduledTaskEntity activeTask() {
        ScheduledTaskEntity task = new ScheduledTaskEntity();
        task.setId("task-id");
        task.setUserId("user-id");
        task.setAgentId("agent-id");
        task.setSessionId("session-id");
        task.setContent("scheduled content");
        task.setStatus(ScheduleTaskStatus.ACTIVE);
        task.setRepeatType(RepeatType.DAILY);
        task.setRepeatConfig(new RepeatConfig(LocalDateTime.now()));
        return task;
    }
}
