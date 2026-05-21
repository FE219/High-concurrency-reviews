package com.hmdp.threadpool.reject;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
public class CacheRejectPolicy implements RejectedExecutionHandler {

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        log.warn("【缓存线程池拒绝任务】active={}, queueSize={}, 任务改为调用方线程执行",
                executor.getActiveCount(),
                executor.getQueue().size());

        // 简化做法：调用方自己执行，避免缓存补偿任务彻底丢失
        if (!executor.isShutdown()) {
            r.run();
        }
    }
}