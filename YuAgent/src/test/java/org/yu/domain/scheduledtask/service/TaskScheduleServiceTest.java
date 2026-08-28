package org.yu.domain.scheduledtask.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.yu.domain.scheduledtask.constant.RepeatType;
import org.yu.domain.scheduledtask.model.RepeatConfig;
import org.yu.domain.scheduledtask.model.ScheduledTaskEntity;

class TaskScheduleServiceTest {

    private final TaskScheduleService taskScheduleService = new TaskScheduleService();

    @Test
    void shouldScheduleMonthlyTaskOnLastDayWhenConfiguredDayDoesNotExist() {
        ScheduledTaskEntity task = monthlyTask(31, LocalDateTime.of(2026, 1, 31, 9, 0));

        LocalDateTime nextTime = taskScheduleService.calculateNextExecuteTime(task,
                LocalDateTime.of(2026, 4, 30, 10, 0));

        assertEquals(LocalDateTime.of(2026, 5, 31, 9, 0), nextTime);
        assertFalse(taskScheduleService.shouldExecuteAt(task, LocalDateTime.of(2026, 2, 27, 9, 0)));
        assertTrue(taskScheduleService.shouldExecuteAt(task, LocalDateTime.of(2026, 2, 28, 9, 0)));
    }

    @Test
    void shouldRejectInvalidRecurringConfigurationsWithoutLooping() {
        ScheduledTaskEntity task = new ScheduledTaskEntity();
        task.setRepeatType(RepeatType.CUSTOM);
        RepeatConfig config = new RepeatConfig(LocalDateTime.of(2026, 1, 1, 9, 0));
        config.setInterval(0);
        config.setTimeUnit("DAYS");
        task.setRepeatConfig(config);

        LocalDateTime now = LocalDateTime.of(2026, 1, 2, 9, 0);
        assertNull(taskScheduleService.calculateNextExecuteTime(task, now));
        assertFalse(taskScheduleService.shouldExecuteAt(task, now));
    }

    @Test
    void shouldHandleMissingExecutionTimeGracefully() {
        ScheduledTaskEntity task = new ScheduledTaskEntity();
        task.setRepeatType(RepeatType.DAILY);
        task.setRepeatConfig(new RepeatConfig());

        assertNull(taskScheduleService.calculateNextExecuteTime(task, LocalDateTime.now()));
    }

    private ScheduledTaskEntity monthlyTask(int monthDay, LocalDateTime executeDateTime) {
        ScheduledTaskEntity task = new ScheduledTaskEntity();
        task.setRepeatType(RepeatType.MONTHLY);
        RepeatConfig config = new RepeatConfig(executeDateTime);
        config.setMonthDay(monthDay);
        task.setRepeatConfig(config);
        return task;
    }
}
