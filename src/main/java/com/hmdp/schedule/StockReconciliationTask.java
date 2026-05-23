package com.hmdp.schedule;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

@Slf4j
@Component
public class StockReconciliationTask {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Scheduled(cron = "0 */5 * * * ?")
    public void reconcileStock() {
        List<SeckillVoucher> vouchers = seckillVoucherService.list();
        for (SeckillVoucher sv : vouchers) {
            String redisKey = SECKILL_STOCK_KEY + sv.getVoucherId();
            String redisStockStr = stringRedisTemplate.opsForValue().get(redisKey);
            if (redisStockStr == null) continue;

            int redisStock = Integer.parseInt(redisStockStr);
            int dbStock = sv.getStock();

            if (Math.abs(redisStock - dbStock) > 5) {
                log.warn("Stock drift detected: voucherId={}, redis={}, db={}",
                        sv.getVoucherId(), redisStock, dbStock);
                stringRedisTemplate.opsForValue().set(redisKey, String.valueOf(dbStock));
            }
        }
    }
}
