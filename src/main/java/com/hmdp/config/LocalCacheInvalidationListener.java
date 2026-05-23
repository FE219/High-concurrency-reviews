package com.hmdp.config;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * 订阅 Redis Pub/Sub 频道，收到失效消息后清除本地 Caffeine 缓存。
 * 解决多实例部署下 L1 缓存一致性问题。
 */
@Slf4j
@Component
public class LocalCacheInvalidationListener implements MessageListener {

    private static final String CHANNEL = "cache:invalidate";

    @Resource
    private Cache<String, Object> localCache;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @PostConstruct
    public void subscribe() {
        new Thread(() -> {
            try {
                stringRedisTemplate.getConnectionFactory()
                        .getConnection()
                        .subscribe(this, CHANNEL.getBytes());
                log.info("L1 invalidation listener subscribed to channel: {}", CHANNEL);
            } catch (Exception e) {
                log.error("Failed to subscribe to cache invalidation channel", e);
            }
        }, "cache-invalidation-listener").start();
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String key = new String(message.getBody());
        localCache.invalidate(key);
        log.debug("L1 cache invalidated: key={}", key);
    }
}
