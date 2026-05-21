package com.hmdp.threadpool;

public final class BizExecutors {

    private BizExecutors() {}

    /**
     * 核心交易线程池：秒杀异步下单
     */
    public static final String SECKILL_ORDER_EXECUTOR = "seckillOrderExecutor";

    /**
     * 次核心线程池：缓存重建、延时双删
     */
    public static final String CACHE_REBUILD_EXECUTOR = "cacheRebuildExecutor";

    /**
     * 非核心线程池：Feed推送、点赞通知、博客分发
     */
    public static final String FEED_DISPATCH_EXECUTOR = "feedDispatchExecutor";

    /**
     * 后台调度线程池：UV统计、排行榜同步、补偿任务
     */
    public static final String SCHEDULE_EXECUTOR = "scheduleExecutor";
}