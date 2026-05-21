package com.hmdp.dto.response;

import lombok.Data;

@Data
public class RecommendationItemDTO {

    private Long shopId;
    private String shopName;
    private Double score;
    private Double distance;
    private String distanceText;
    private Double avgPrice;
    private String avgPriceText;
    private String reason;
    private String area;
    private String openHours;

    private Boolean hasCoupon;
    private Integer couponCount;
    private String couponSummary;

    /**
     * 推荐综合分，方便调试
     */
    private Double recommendScore;
}