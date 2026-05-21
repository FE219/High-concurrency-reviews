package com.hmdp.tool;


import com.hmdp.dto.tool.VoucherSimpleDTO;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IVoucherService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

import cn.hutool.core.collection.CollUtil;
import java.util.stream.Collectors;

@Component
public class CouponTool {

    @Resource
    private IVoucherService voucherService;

    /**
     * 查询某店铺所有优惠券
     */
    public List<VoucherSimpleDTO> queryShopCoupons(Long shopId) {
        if (shopId == null) {
            return Collections.emptyList();
        }
        List<Voucher> vouchers = voucherService.queryVoucherEntitiesOfShop(shopId);
        if (CollUtil.isEmpty(vouchers)) {
            return Collections.emptyList();
        }

        return vouchers.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private VoucherSimpleDTO toDTO(Voucher voucher) {
        VoucherSimpleDTO dto = new VoucherSimpleDTO();
        dto.setVoucherId(voucher.getId());
        dto.setShopId(voucher.getShopId());
        dto.setTitle(voucher.getTitle());
        dto.setSubTitle(voucher.getSubTitle());
        dto.setRules(voucher.getRules());
        dto.setStatus(voucher.getStatus());

        // 数据库存的是分，这里统一转成元
        if (voucher.getPayValue() != null) {
            dto.setPayValue(voucher.getPayValue() / 100.0);
        }
        if (voucher.getActualValue() != null) {
            dto.setActualValue(voucher.getActualValue() / 100.0);
        }

        // 0=普通券，1=秒杀券
        dto.setSeckill(voucher.getType() != null && voucher.getType() == 1);

        return dto;
    }
}