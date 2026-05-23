package com.hmdp.mq;

import com.hmdp.constant.ErrorCode;
import com.hmdp.exception.BusinessException;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.Message;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Map;

@Slf4j
@RocketMQTransactionListener
public class SeckillOrderTransactionListener implements RocketMQLocalTransactionListener {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> COMPENSATE_SCRIPT;
    static {
        COMPENSATE_SCRIPT = new DefaultRedisScript<>();
        COMPENSATE_SCRIPT.setLocation(
            new ClassPathResource("compensate-seckill.lua"));
        COMPENSATE_SCRIPT.setResultType(Long.class);
    }

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) msg.getPayload();
        Long voucherId = toLong(payload.get("voucherId"));
        Long userId = toLong(payload.get("userId"));
        Long orderId = toLong(payload.get("orderId"));

        try {
            voucherOrderService.createVoucherOrderFromMq(voucherId, userId, orderId);
            log.info("Local transaction commit: orderId={}", orderId);
            return RocketMQLocalTransactionState.COMMIT;
        } catch (BusinessException e) {
            log.error("Business error, rolling back: orderId={}, error={}", orderId, e.getMessage());
            compensateRedisStock(voucherId, userId);
            return RocketMQLocalTransactionState.ROLLBACK;
        } catch (Exception e) {
            log.error("Unknown error, will retry: orderId={}", orderId, e);
            return RocketMQLocalTransactionState.UNKNOWN;
        }
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) msg.getPayload();
        Long voucherId = toLong(payload.get("voucherId"));
        Long userId = toLong(payload.get("userId"));
        Long orderId = toLong(payload.get("orderId"));

        boolean exists = voucherOrderService.orderExists(voucherId, userId);
        if (exists) {
            log.info("Recheck: order exists, commit: orderId={}", orderId);
            return RocketMQLocalTransactionState.COMMIT;
        }
        log.warn("Recheck: order not found, rolling back: orderId={}", orderId);
        compensateRedisStock(voucherId, userId);
        return RocketMQLocalTransactionState.ROLLBACK;
    }

    private void compensateRedisStock(Long voucherId, Long userId) {
        stringRedisTemplate.execute(
                COMPENSATE_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        log.info("Redis stock compensated: voucherId={}, userId={}", voucherId, userId);
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
