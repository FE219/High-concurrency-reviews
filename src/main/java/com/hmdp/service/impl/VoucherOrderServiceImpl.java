package com.hmdp.service.impl;

import com.hmdp.annotation.AuditLog;
import com.hmdp.constant.ErrorCode;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.exception.BusinessException;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.mq.RocketMQTransactionProducer;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RocketMQTransactionProducer transactionProducer;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private static final DefaultRedisScript<Long> COMPENSATE_SCRIPT;
    static {
        COMPENSATE_SCRIPT = new DefaultRedisScript<>();
        COMPENSATE_SCRIPT.setLocation(
            new ClassPathResource("compensate-seckill.lua"));
        COMPENSATE_SCRIPT.setResultType(Long.class);
    }

    @Override
    @AuditLog
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");

        // 1. Lua 原子预占库存（Redis 层面防重 + 扣库存，不再 XADD）
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        int r = result.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        // 2. RocketMQ 事务消息（MySQL 落库）
        boolean sent = transactionProducer.sendSeckillOrder(voucherId, userId, orderId);
        if (!sent) {
            // 发送失败 → 补偿 Redis 库存
            compensateRedisStock(voucherId, userId);
            return Result.fail("系统繁忙，请稍后重试");
        }

        return Result.ok(orderId);
    }

    @Override
    @Transactional
    public void createVoucherOrderFromMq(Long voucherId, Long userId, Long orderId) {
        // 1. 一人一单
        Long count = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        if (count > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_ORDER,
                    "userId=" + userId + ", voucherId=" + voucherId);
        }

        // 2. 扣减库存（失败抛异常，触发事务回滚 + MQ Rollback）
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT,
                    "voucherId=" + voucherId);
        }

        // 3. 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
    }

    @Override
    public boolean orderExists(Long voucherId, Long userId) {
        return query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count() > 0;
    }

    @Override
    public boolean closeOrder(Long orderId, Integer version) {
        // WHERE id = ? AND status = 1 AND version = ?
        // 支付回调若先改了 status 或 version，affected rows = 0，关单自动跳过
        boolean success = lambdaUpdate()
                .eq(VoucherOrder::getId, orderId)
                .eq(VoucherOrder::getStatus, 1)
                .eq(VoucherOrder::getVersion, version)
                .set(VoucherOrder::getStatus, 4)
                .set(VoucherOrder::getVersion, version + 1)
                .set(VoucherOrder::getUpdateTime, java.time.LocalDateTime.now())
                .update();
        return success;
    }

    private void compensateRedisStock(Long voucherId, Long userId) {
        stringRedisTemplate.execute(
                COMPENSATE_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
    }
}
