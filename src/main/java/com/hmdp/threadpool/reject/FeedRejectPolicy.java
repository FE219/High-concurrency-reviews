package com.hmdp.threadpool.reject;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
public class FeedRejectPolicy implements RejectedExecutionHandler {

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        log.warn("【Feed线程池拒绝任务】active={}, queueSize={}, 非核心任务直接丢弃",
                executor.getActiveCount(),
                executor.getQueue().size());
        // 非核心任务降级丢弃
    }
}