package com.hmdp.dto.tool;

import lombok.Data;

@Data
public class ShopSimpleDTO {

    private Long shopId;

    /**
     * 店铺名称
     */
    private String name;

    /**
     * 评分，已转成真实值，如 4.9
     */
    private Double score;

    /**
     * 距离，单位米
     */
    private Double distance;

    /**
     * 人均消费，单位元
     */
    private Double avgPrice;

    private String address;

    /**
     * 经度
     */
    private Double x;

    /**
     * 纬度
     */
    private Double y;

    /**
     * 店铺大类ID，对应 tb_shop.type_id
     */
    private Long typeId;

    /**
     * 商圈
     */
    private String area;

    /**
     * 营业时间
     */
    private String openHours;
}