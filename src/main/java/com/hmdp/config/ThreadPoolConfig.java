package com.hmdp.config;

import com.hmdp.threadpool.BizExecutors;
import com.hmdp.threadpool.NamedThreadFactory;
import com.hmdp.threadpool.reject.CacheRejectPolicy;
import com.hmdp.threadpool.reject.FeedRejectPolicy;
import com.hmdp.threadpool.reject.ScheduleRejectPolicy;
import com.hmdp.threadpool.reject.SeckillRejectPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public ExecutorService seckillOrderExecutor() {
        return new ThreadPoolExecutor(
                8,                      // corePoolSize
                16,                     // maxPoolSize
                60L, TimeUnit.SECONDS,  // keepAliveTime
                new ArrayBlockingQueue<>(200), // 小队列
                new NamedThreadFactory("seckill-order"),
                new SeckillRejectPolicy()
        );
    }

    /**
     * 次核心线程池：缓存重建、延时双删
     * 特点：
     * 1. 允许适度堆积
     * 2. 不能轻易丢任务
     * 3. 可退化为调用方执行
     */
    @Bean(name = BizExecutors.CACHE_REBUILD_EXECUTOR)
    public ExecutorService cacheRebuildExecutor() {
        return new ThreadPoolExecutor(
                4,
                8,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(500),
                new NamedThreadFactory("cache-rebuild"),
                new CacheRejectPolicy()
        );
    }

    /**
     * 非核心线程池：Feed推送、点赞通知、博客分发
     * 特点：
     * 1. 优先级低
     * 2. 允许降级丢弃
     */
    @Bean(name = BizExecutors.FEED_DISPATCH_EXECUTOR)
    public ExecutorService feedDispatchExecutor() {
        return new ThreadPoolExecutor(
                4,
                8,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                new NamedThreadFactory("feed-dispatch"),
                new FeedRejectPolicy()
        );
    }

    /**
     * 后台调度线程池：UV统计、排行榜同步、补偿任务
     */
    @Bean(name = BizExecutors.SCHEDULE_EXECUTOR)
    public ScheduledExecutorService scheduleExecutor() {
        return new ScheduledThreadPoolExecutor(
                4,
                new NamedThreadFactory("schedule-task"),
                new ScheduleRejectPolicy()
        );
    }
}