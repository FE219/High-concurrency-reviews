package com.hmdp.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DelayDoubleDelete {

    private final StringRedisTemplate stringRedisTemplate;

    @Async
    public void deleteWithDelay(String key, long delayMs) {
        try {
            Thread.sleep(delayMs);
            stringRedisTemplate.delete(key);
            log.debug("Delay double-delete: key={}, delayMs={}", key, delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
