package com.hmdp.config;

import com.hmdp.threadpool.BizExecutors;
import com.hmdp.threadpool.NamedThreadFactory;
import com.hmdp.threadpool.reject.CacheRejectPolicy;
import com.hmdp.threadpool.reject.FeedRejectPolicy;
import com.hmdp.threadpool.reject.ScheduleRejectPolicy;
import com.hmdp.threadpool.reject.SeckillRejectPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.*;

@Configuration
public class ThreadPoolConfig {

    /**
     * 核心交易线程池：秒杀异步下单
     * 特点：
     * 1. 小队列，避免长时间堆积
     * 2. 快速失败
     * 3. 独立隔离，防止被其他异步任务抢占
     */
    @Bean(name = BizExecutors.SECKILL_ORDER_EXECUTOR)
    public ExecutorService seckillOrderExecutor(TaskDecorator taskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setKeepAliveSeconds(60);
        executor.setQueueCapacity(200);
        executor.setThreadFactory(new NamedThreadFactory("seckill-order"));
        executor.setRejectedExecutionHandler(new SeckillRejectPolicy());
        executor.setTaskDecorator(taskDecorator);
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }

    /**
     * 次核心线程池：缓存重建、延时双删
     * 特点：
     * 1. 允许适度堆积
     * 2. 不能轻易丢任务
     * 3. 可退化为调用方执行
     */
    @Bean(name = BizExecutors.CACHE_REBUILD_EXECUTOR)
    public ExecutorService cacheRebuildExecutor(TaskDecorator taskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setKeepAliveSeconds(60);
        executor.setQueueCapacity(500);
        executor.setThreadFactory(new NamedThreadFactory("cache-rebuild"));
        executor.setRejectedExecutionHandler(new CacheRejectPolicy());
        executor.setTaskDecorator(taskDecorator);
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }

    /**
     * 非核心线程池：Feed推送、点赞通知、博客分发
     * 特点：
     * 1. 优先级低
     * 2. 允许降级丢弃
     */
    @Bean(name = BizExecutors.FEED_DISPATCH_EXECUTOR)
    public ExecutorService feedDispatchExecutor(TaskDecorator taskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setKeepAliveSeconds(60);
        executor.setQueueCapacity(1000);
        executor.setThreadFactory(new NamedThreadFactory("feed-dispatch"));
        executor.setRejectedExecutionHandler(new FeedRejectPolicy());
        executor.setTaskDecorator(taskDecorator);
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }

    /**
     * 后台调度线程池：UV统计、排行榜同步、补偿任务
     */
    @Bean(name = BizExecutors.SCHEDULE_EXECUTOR)
    public ScheduledExecutorService scheduleExecutor(TaskDecorator taskDecorator) {
        return new ScheduledThreadPoolExecutor(
                4,
                new NamedThreadFactory("schedule-task"),
                new ScheduleRejectPolicy()
        ) {
            @Override
            public void execute(Runnable command) {
                super.execute(taskDecorator.decorate(command));
            }
        };
    }
}
