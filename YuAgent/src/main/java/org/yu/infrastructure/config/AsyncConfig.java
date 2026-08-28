package org.yu.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/** 异步配置 启用Spring的异步处理功能，用于异步事件处理 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** 未显式指定执行器的 {@code @Async} 事件使用的默认线程池。
     *
     * <p>
     * 若没有名为 {@code taskExecutor} 的 Bean，Spring 会在多个具名执行器存在时回退到无界的
     * {@code SimpleAsyncTaskExecutor}。对充值、会话和高可用事件而言，这会在瞬时峰值下无限创建线程。 此处使用有界队列，并在饱和时让调用方承担背压，优先保证事件不被悄悄丢弃。
     * </p>
     */
    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("app-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /** 专用于记忆抽取与持久化的线程池，避免与其他异步任务互相影响 */
    @Bean(name = "memoryTaskExecutor")
    public ThreadPoolTaskExecutor memoryTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("memory-async-");
        // 繁忙时在调用线程执行，确保不丢任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /** 首次对话自动命名的专用线程池，避免慢模型调用占用请求线程或无限制创建线程。 */
    @Bean(name = "sessionTitleTaskExecutor")
    public ThreadPoolTaskExecutor sessionTitleTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("session-title-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    /** OCR 请求需要等待外部模型响应，不能占用 JDK 公共 ForkJoinPool。
     *
     * <p>
     * 有界队列让 OCR 峰值可预测；饱和时拒绝当前页，由上层记录失败并继续处理其余页面，避免把 文档处理线程长期阻塞在提交阶段。
     * </p>
     */
    @Bean(name = "ocrTaskExecutor")
    public ThreadPoolTaskExecutor ocrTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("pdf-ocr-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
