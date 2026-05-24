package com.hmdp.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class RocketMQTransactionProducer {

    private static final String TOPIC = "seckill-order-topic";
    private static final String CLOSE_TOPIC = "order-close-topic";

    /**
     * RocketMQ 18 级延迟：16 = 30 分钟
     */
    private static final int DELAY_LEVEL_30M = 16;

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    public boolean sendSeckillOrder(Long voucherId, Long userId, Long orderId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("voucherId", voucherId);
        payload.put("userId", userId);
        payload.put("orderId", orderId);

        Message<Map<String, Object>> message = MessageBuilder
                .withPayload(payload)
                .build();

        try {
            rocketMQTemplate.sendMessageInTransaction(TOPIC, message, null);
            log.info("Transaction message sent: orderId={}", orderId);
            return true;
        } catch (Exception e) {
            log.error("Failed to send transaction message, orderId={}", orderId, e);
            return false;
        }
    }

    /**
     * 发送延迟关单消息。订单创建成功后调用，30 分钟后投递。
     */
    public void sendOrderCloseDelay(Long orderId, Long voucherId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("voucherId", voucherId);

        Message<Map<String, Object>> message = MessageBuilder
                .withPayload(payload)
                .build();

        rocketMQTemplate.syncSend(CLOSE_TOPIC, message,
                3000, DELAY_LEVEL_30M);
        log.info("Delay close message sent: orderId={}, delayLevel={}", orderId, DELAY_LEVEL_30M);
    }
}
