package com.hmdp.threadpool.monitor;

import com.hmdp.threadpool.BizExecutors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Component
@RequiredArgsConstructor
public class ThreadPoolMonitor {

    @Resource(name = BizExecutors.SECKILL_ORDER_EXECUTOR)
    private ExecutorService seckillExecutor;

    @Resource(name = BizExecutors.CACHE_REBUILD_EXECUTOR)
    private ExecutorService cacheExecutor;

    @Resource(name = BizExecutors.FEED_DISPATCH_EXECUTOR)
    private ExecutorService feedExecutor;

    @Resource(name = BizExecutors.SCHEDULE_EXECUTOR)
    private ScheduledExecutorService scheduleExecutor;

    @Scheduled(fixedDelay = 10000)
    public void monitor() {
        print("seckillExecutor", (ThreadPoolExecutor) seckillExecutor);
        print("cacheExecutor", (ThreadPoolExecutor) cacheExecutor);
        print("feedExecutor", (ThreadPoolExecutor) feedExecutor);
        print("scheduleExecutor", (ThreadPoolExecutor) scheduleExecutor);
    }

    private void print(String name, ThreadPoolExecutor executor) {
        log.info("【线程池监控】name={}, core={}, max={}, poolSize={}, active={}, queueSize={}, completed={}",
                name,
                executor.getCorePoolSize(),
                executor.getMaximumPoolSize(),
                executor.getPoolSize(),
                executor.getActiveCount(),
                executor.getQueue().size(),
                executor.getCompletedTaskCount());
    }
}