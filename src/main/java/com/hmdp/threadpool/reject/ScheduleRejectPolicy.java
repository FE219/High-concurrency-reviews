package com.hmdp.threadpool.reject;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
public class ScheduleRejectPolicy implements RejectedExecutionHandler {

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        log.error("【调度线程池拒绝任务】active={}, queueSize={}, 请检查后台任务是否堆积",
                executor.getActiveCount(),
                executor.getQueue().size());
        // 后台任务通常不建议调用方执行，防止拖慢调度线程
        // 这里只做日志告警，后续可扩展为落库补偿/重试
    }
}