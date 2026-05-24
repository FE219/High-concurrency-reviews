package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    void createVoucherOrderFromMq(Long voucherId, Long userId, Long orderId);

    boolean orderExists(Long voucherId, Long userId);

    /**
     * 关闭未支付订单（乐观锁版本号防并发冲突）
     * @return true=关单成功, false=订单已被支付或已取消
     */
    boolean closeOrder(Long orderId, Integer version);

    /**
     * 关单 + 回滚 MySQL/Redis 库存。延迟队列到期时调用。
     */
    void closeOrderWithStockRollback(Long orderId, Integer version, Long voucherId);
}
