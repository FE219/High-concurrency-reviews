package com.hmdp.schedule;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 未支付订单到期自动关闭。
 * 每分钟扫描创建超过 15 分钟且状态为"未支付"的订单，
 * 使用乐观锁版本号防止与支付回调并发冲突。
 */
@Slf4j
@Component
public class OrderCloseTask {

    /**
     * 订单超时时间（分钟）
     */
    private static final long EXPIRE_MINUTES = 15;

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Scheduled(cron = "0 */1 * * * ?")
    public void closeExpiredOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(EXPIRE_MINUTES);

        // 查询超时未支付订单（状态 1 = 未支付）
        List<VoucherOrder> expiredOrders = voucherOrderService.query()
                .eq("status", 1)
                .lt("create_time", deadline)
                .list();

        if (expiredOrders.isEmpty()) {
            return;
        }

        log.info("Found {} expired unpaid orders, deadline={}", expiredOrders.size(), deadline);

        int closed = 0;
        int skipped = 0;
        for (VoucherOrder order : expiredOrders) {
            boolean success = voucherOrderService.closeOrder(order.getId(), order.getVersion());
            if (success) {
                closed++;
                log.debug("Order closed: orderId={}, userId={}", order.getId(), order.getUserId());
            } else {
                skipped++;
                log.debug("Order skip (already paid/canceled): orderId={}", order.getId());
            }
        }

        log.info("Order close batch done: closed={}, skipped={}", closed, skipped);
    }
}
