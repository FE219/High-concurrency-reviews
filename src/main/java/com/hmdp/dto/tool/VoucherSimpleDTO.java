package com.hmdp.dto.tool;
import lombok.Data;

@Data
public class VoucherSimpleDTO {

    private Long voucherId;

    private Long shopId;

    private String title;

    private String subTitle;

    /**
     * 支付金额，单位：元
     */
    private Double payValue;

    /**
     * 抵扣金额，单位：元
     */
    private Double actualValue;

    /**
     * 是否秒杀券
     */
    private Boolean seckill;

    /**
     * 使用规则
     */
    private String rules;

    /**
     * 券状态：1上架 2下架 3过期
     */
    private Integer status;
}