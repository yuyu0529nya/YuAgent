package org.yu.domain.scheduledtask.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.yu.domain.scheduledtask.model.DelayedTaskItem;
import org.yu.domain.scheduledtask.model.ScheduledTaskEntity;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;

/** 延迟队列管理器 负责管理延迟队列和任务调度 */
@Service
public class DelayedTaskQueueManager {

    private static final Logger logger = LoggerFactory.getLogger(DelayedTaskQueueManager.class);

    /** 延迟队列 */
    private final DelayQueue<DelayedTaskItem> delayQueue = new DelayQueue<>();

    /** 每个任务在队列中只保留一个最新调度项。DelayQueue 不会基于 equals 自动去重， 因此需要显式维护这个索引，防止重试、恢复或重新调度时同一任务被执行多次。 */
    private final ConcurrentMap<String, DelayedTaskItem> scheduledItems = new ConcurrentHashMap<>();

    /** 任务执行器 */
    private final ScheduleTaskExecutor taskExecutor;

    /** 线程池 */
    private ExecutorService executorService;

    /** 队列消费线程 */
    private Thread consumerThread;

    /** 是否运行中 */
    private volatile boolean running = false;

    public DelayedTaskQueueManager(ScheduleTaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    /** 初始化队列管理器 */
    @PostConstruct
    public void init() {
        // 定时任务可能等待模型或外部工具响应。固定线程池默认使用无界队列，突发积压会无限占用堆内存。
        // 队列饱和时由消费线程执行任务以形成背压，避免悄悄丢失已到期任务。
        this.executorService = new ThreadPoolExecutor(5, 5, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(100),
                r -> {
                    Thread t = new Thread(r, "scheduled-task-executor-");
                    t.setDaemon(true);
                    return t;
                }, new ThreadPoolExecutor.CallerRunsPolicy());

        // 启动队列消费线程
        startConsumer();

        logger.info("延迟队列管理器已启动，线程池大小: 5");
    }

    /** 销毁队列管理器 */
    @PreDestroy
    public void destroy() {
        running = false;

        // 中断消费线程
        if (consumerThread != null) {
            consumerThread.interrupt();
        }

        // 关闭线程池
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        logger.info("延迟队列管理器已关闭");
    }

    /** 添加任务到延迟队列
     * @param task 定时任务实体
     * @param executeTime 执行时间 */
    public void addTask(ScheduledTaskEntity task, LocalDateTime executeTime) {
        DelayedTaskItem item = new DelayedTaskItem(task, executeTime);
        DelayedTaskItem previousItem = scheduledItems.put(item.getTaskId(), item);
        if (previousItem != null) {
            delayQueue.remove(previousItem);
        }
        delayQueue.offer(item);
        logger.info("任务已添加到延迟队列: taskId={}, executeTime={}", task.getId(), executeTime);
    }

    /** 移除任务从延迟队列
     * @param taskId 任务ID */
    public void removeTask(String taskId) {
        DelayedTaskItem scheduledItem = scheduledItems.remove(taskId);
        boolean removed = scheduledItem != null && delayQueue.remove(scheduledItem);
        if (removed) {
            logger.info("任务已从延迟队列移除: taskId={}", taskId);
        } else {
            logger.debug("任务不在延迟队列中: taskId={}", taskId);
        }
    }

    /** 获取队列大小
     * @return 队列大小 */
    public int getQueueSize() {
        return delayQueue.size();
    }

    /** 启动队列消费线程 */
    private void startConsumer() {
        running = true;
        consumerThread = new Thread(this::consumeQueue, "delayed-task-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();
        logger.info("延迟队列消费线程已启动");
    }

    /** 队列消费逻辑 */
    private void consumeQueue() {
        logger.info("延迟队列消费线程开始运行");

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                // 从延迟队列中取出到期的任务
                DelayedTaskItem item = delayQueue.take();

                if (item != null && scheduledItems.remove(item.getTaskId(), item)) {
                    logger.debug("从延迟队列取出到期任务: taskId={}", item.getTaskId());

                    // 提交任务到线程池执行
                    executorService.submit(() -> {
                        try {
                            ScheduledTaskEntity task = taskExecutor.getExecutableTask(item.getTask());

                            if (task != null && taskExecutor.executeTask(task)) {
                                scheduleNextExecution(task);
                            } else {
                                logger.info("任务未执行或执行未完成，跳过后续调度: taskId={}", item.getTaskId());
                            }
                        } catch (Exception e) {
                            logger.error("执行任务异常: taskId={}, error={}", item.getTaskId(), e.getMessage(), e);
                        }
                    });
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("队列消费异常: {}", e.getMessage(), e);

                // 短暂休眠后继续
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        logger.info("延迟队列消费线程已停止");
    }

    /** 检查并调度任务的下次执行
     * @param task 已执行的任务 */
    private void scheduleNextExecution(ScheduledTaskEntity task) {
        try {
            // executeTask 会更新同一任务对象的状态和下次执行时间。
            if (task.isActive() && task.getNextExecuteTime() != null) {
                LocalDateTime nextTime = task.getNextExecuteTime();

                // 只有未来的时间才需要调度
                if (nextTime.isAfter(LocalDateTime.now())) {
                    addTask(task, nextTime);
                    logger.info("任务已重新调度: taskId={}, nextTime={}", task.getId(), nextTime);
                }
            }
        } catch (Exception e) {
            logger.error("重新调度任务失败: taskId={}, error={}", task.getId(), e.getMessage(), e);
        }
    }
}
