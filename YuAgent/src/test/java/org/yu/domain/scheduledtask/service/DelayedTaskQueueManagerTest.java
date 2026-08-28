package org.yu.domain.scheduledtask.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.yu.domain.scheduledtask.model.ScheduledTaskEntity;

class DelayedTaskQueueManagerTest {

    @Test
    void keepsOnlyTheLatestScheduleForEachTask() {
        DelayedTaskQueueManager queueManager = new DelayedTaskQueueManager(null);
        ScheduledTaskEntity task = task("task-1");

        queueManager.addTask(task, LocalDateTime.now().plusMinutes(5));
        queueManager.addTask(task, LocalDateTime.now().plusMinutes(10));

        assertEquals(1, queueManager.getQueueSize());
    }

    @Test
    void removesTheTrackedSchedule() {
        DelayedTaskQueueManager queueManager = new DelayedTaskQueueManager(null);
        queueManager.addTask(task("task-1"), LocalDateTime.now().plusMinutes(5));

        queueManager.removeTask("task-1");

        assertEquals(0, queueManager.getQueueSize());
    }

    private ScheduledTaskEntity task(String id) {
        ScheduledTaskEntity task = new ScheduledTaskEntity();
        task.setId(id);
        return task;
    }
}
