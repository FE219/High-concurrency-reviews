package com.hmdp.threadpool.reject;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
public class SeckillRejectPolicy implements RejectedExecutionHandler {

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        log.error("【秒杀线程池拒绝任务】active={}, poolSize={}, core={}, max={}, queueSize={}",
                executor.getActiveCount(),
                executor.getPoolSize(),
                executor.getCorePoolSize(),
                executor.getMaximumPoolSize(),
                executor.getQueue().size());

        throw new RuntimeException("系统繁忙，请稍后重试");
    }
}