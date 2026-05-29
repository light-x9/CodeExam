package com.atguigu.exam.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置
 * <p>
 * 为什么需要自定义线程池？
 * - Spring Boot 默认的 SimpleAsyncTaskExecutor 每次任务都创建新线程，等同于 new Thread()
 * - 线程创建/销毁开销大，高并发下会 OOM
 * - 线程池复用线程，控制资源，有界队列提供背压
 */
@Slf4j
@Configuration
@EnableAsync  // 开启 Spring 异步支持，让 @Async 注解生效
public class AsyncConfig {

    /**
     * 题目热榜分数更新的专用线程池
     * <p>
     * 为什么单独定义一个 Bean 而不是直接用默认？
     * - 不同业务场景需要不同的线程池参数
     * - 可以通过 @Async("beanName") 指定使用哪个线程池
     * - 后续如果其他业务需要异步，可以再定义不同的线程池 Bean
     */
    @Bean(name = "questionScoreExecutor")
    public ThreadPoolTaskExecutor questionScoreExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // ── 核心线程数：CPU 核心数 ──
        // 这个任务主要是 Redis 网络 I/O（ZINCRBY），几乎不消耗 CPU
        // 设为核心数即可常驻处理日常流量，避免频繁创建/销毁线程
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(corePoolSize);

        // ── 最大线程数：核心数 × 2 ──
        // 应对突发流量时临时扩容，峰值过后空闲线程会被回收
        executor.setMaxPoolSize(corePoolSize * 2);

        // ── 队列容量：200 ──
        // 线程池工作流程：
        //   1. 任务进来 → 先分配给核心线程
        //   2. 核心线程满了 → 放入队列等待
        //   3. 队列满了 → 创建新线程（直到 maxPoolSize）
        //   4. 队列+线程都满了 → 触发拒绝策略
        // 200 的容量在考试系统中足够缓冲查询高峰
        executor.setQueueCapacity(200);

        // ── 空闲线程存活时间：60 秒 ──
        // 超出核心数的线程在空闲 60 秒后被销毁，释放资源
        executor.setKeepAliveSeconds(60);

        // ── 线程名前缀：方便排查问题 ──
        // 日志中看到 "async-question-3" 就知道是这个线程池的任务
        executor.setThreadNamePrefix("async-question-");

        // ── 拒绝策略：CallerRunsPolicy（调用者运行） ──
        // 当队列满 + 线程数达到 max 时，任务交还给调用线程（HTTP 请求线程）同步执行
        // 优点：保证任务不丢失，同时产生天然背压（调用线程变慢 → 外部请求自然降速）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // ── 优雅停机 ──
        // Spring 容器关闭时，等待队列中剩余任务执行完再销毁线程
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 最多等待 30 秒，超时后强制关闭
        executor.setAwaitTerminationSeconds(30);

        // 初始化线程池（必须调用，否则参数不生效）
        executor.initialize();

        log.info("异步线程池初始化完成: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                corePoolSize, corePoolSize * 2, 200);
        return executor;
    }

    /**
     * AI 判卷专用线程池
     *
     * 为什么单独建一个线程池？
     * - AI 判卷是网络 I/O 密集型（调用 Kimi API，单次耗时 3~10 秒）
     * - 与热榜分数更新的轻量 Redis 操作隔离，避免互相影响
     * - 核心线程数较少即可，因为瓶颈在外部 API 响应速度
     */
    @Bean(name = "gradingExecutor")
    public ThreadPoolTaskExecutor gradingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // AI 判卷是纯网络等待，核心线程数不需要多，2 个即可
        executor.setCorePoolSize(2);
        // 突发时可扩展到 4 个
        executor.setMaxPoolSize(4);
        // 队列容量 50 —— AI 调用耗时长，排队多了也没意义，不如快速失败
        executor.setQueueCapacity(50);
        // 空闲线程 120 秒后回收
        executor.setKeepAliveSeconds(120);
        // 线程名前缀方便在日志中识别判卷任务
        executor.setThreadNamePrefix("ai-grading-");
        // 拒绝策略：队列满 + 线程满 → 交还给调用线程同步执行（背压）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅停机：等待队列中任务完成
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();

        log.info("AI判卷线程池初始化完成: corePoolSize=2, maxPoolSize=4, queueCapacity=50");
        return executor;
    }
}
