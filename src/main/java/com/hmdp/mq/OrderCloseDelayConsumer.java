package com.hmdp.mq;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 延迟关单消费者。
 * 下单时发送延迟 30 分钟的消息，到期后检查订单状态：
 *   状态仍为"未支付" → 关单 + 回滚库存
 *   状态已是"已支付/已取消" → 直接丢弃
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "order-close-topic",
        consumerGroup = "${rocketmq.consumer.group}-close")
public class OrderCloseDelayConsumer implements RocketMQListener<Map<String, Object>> {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Override
    public void onMessage(Map<String, Object> payload) {
        Long orderId = toLong(payload.get("orderId"));
        Long voucherId = toLong(payload.get("voucherId"));

        if (orderId == null) {
            log.error("Invalid close delay message: orderId is null");
            return;
        }

        // 查订单当前状态
        VoucherOrder order = voucherOrderService.getById(orderId);
        if (order == null) {
            log.warn("Order not found: orderId={}", orderId);
            return;
        }

        // 只关未支付的
        if (order.getStatus() != 1) {
            log.info("Order status changed, skip close: orderId={}, status={}", orderId, order.getStatus());
            return;
        }

        // 关单 + 回滚库存
        voucherOrderService.closeOrderWithStockRollback(order.getId(), order.getVersion(), voucherId);
        log.info("Delay close executed: orderId={}, voucherId={}", orderId, voucherId);
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
