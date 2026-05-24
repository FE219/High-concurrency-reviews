package com.hmdp.schedule;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * 库存对账任务。
 *
 * 核心原则：
 * 1. 活动进行中，Redis 是权威数据源（Lua 原子扣减），绝不碰 Redis。
 * 2. 活动结束后，等 MQ 消息消费完（grace period），以 MySQL 为准修正 Redis。
 * 3. 修正使用 Lua CAS（Compare-And-Swap）防并发覆盖 ——
 *    只覆盖"自上次读取后未被改动"的值，否则跳过。
 */
@Slf4j
@Component
public class StockReconciliationTask {

    private static final long GRACE_MINUTES = 5;

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * Lua CAS：仅当当前值等于 expectedValue 时才写入 newValue。
     * 返回值：1=写入成功, 0=值已被改动(跳过)
     */
    private static final DefaultRedisScript<Long> CAS_SET_SCRIPT;
    static {
        CAS_SET_SCRIPT = new DefaultRedisScript<>();
        CAS_SET_SCRIPT.setLocation(new ClassPathResource("cas-set.lua"));
        CAS_SET_SCRIPT.setResultType(Long.class);
    }

    @Scheduled(cron = "0 */5 * * * ?")
    public void reconcileStock() {
        LocalDateTime now = LocalDateTime.now();

        List<SeckillVoucher> vouchers = seckillVoucherService.list();
        for (SeckillVoucher sv : vouchers) {
            // ==== 1. 活动进行中：不碰 Redis ====
            if (sv.getEndTime() != null && sv.getEndTime().isAfter(now)) {
                log.debug("Skip active seckill: voucherId={}, endTime={}", sv.getVoucherId(), sv.getEndTime());
                continue;
            }

            // ==== 2. 活动刚结束 5 分钟内：等 MQ 消息消费完 ====
            if (sv.getEndTime() != null
                    && sv.getEndTime().plusMinutes(GRACE_MINUTES).isAfter(now)) {
                log.debug("Skip grace period: voucherId={}", sv.getVoucherId());
                continue;
            }

            // ==== 3. 活动结束后：MySQL 为最终权威，Redis 可被修正 ====
            String redisKey = SECKILL_STOCK_KEY + sv.getVoucherId();
            String redisStockStr = stringRedisTemplate.opsForValue().get(redisKey);
            if (redisStockStr == null) {
                continue;
            }

            int redisStock = Integer.parseInt(redisStockStr);
            int dbStock = sv.getStock();

            if (redisStock == dbStock) {
                continue;
            }

            log.warn("Stock drift detected after seckill end: voucherId={}, redis={}, db={}",
                    sv.getVoucherId(), redisStock, dbStock);

            // CAS 写入：只有当 Redis 值未被其他线程改动时才覆盖
            Long result = stringRedisTemplate.execute(
                    CAS_SET_SCRIPT,
                    Collections.singletonList(redisKey),
                    String.valueOf(redisStock),   // expectedValue
                    String.valueOf(dbStock)        // newValue
            );

            if (result != null && result == 1) {
                log.info("Stock corrected: voucherId={}, {} -> {}",
                        sv.getVoucherId(), redisStock, dbStock);
            } else {
                log.warn("Stock CAS skipped (value changed during read): voucherId={}",
                        sv.getVoucherId());
            }
        }
    }
}
