package com.hmdp.shopmatcher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopNameRefreshListener implements MessageListener {

    private final ShopNameMatcher shopNameMatcher;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @PostConstruct
    public void init() {
        new Thread(() -> {
            try {
                stringRedisTemplate.getConnectionFactory()
                    .getConnection()
                    .subscribe(this, "shop:name:refresh".getBytes());
            } catch (Exception e) {
                log.error("Failed to subscribe to shop:name:refresh", e);
            }
        }, "shop-name-refresh-listener").start();
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        log.info("Shop name refresh triggered");
        shopNameMatcher.refresh();
    }
}
