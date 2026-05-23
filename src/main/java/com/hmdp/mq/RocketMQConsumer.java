package com.hmdp.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RocketMQMessageListener(topic = "seckill-order-topic",
        consumerGroup = "${rocketmq.consumer.group}")
public class RocketMQConsumer implements RocketMQListener<Map<String, Object>> {

    @Override
    public void onMessage(Map<String, Object> payload) {
        Long orderId = toLong(payload.get("orderId"));
        Long userId = toLong(payload.get("userId"));
        Long voucherId = toLong(payload.get("voucherId"));

        log.info("Order committed: orderId={}, userId={}, voucherId={}", orderId, userId, voucherId);
        // Placeholder for downstream: send notification, sync cache, etc.
    }

    private Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            return Long.valueOf((String) value);
        }
        return null;
    }
}
