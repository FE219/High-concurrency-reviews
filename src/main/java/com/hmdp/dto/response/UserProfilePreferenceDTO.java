package com.hmdp.dto.response;

import lombok.Data;

import java.util.Map;

@Data
public class UserProfilePreferenceDTO {

    /**
     * 品类偏好，如 火锅=8, 日料=3
     */
    private Map<String, Integer> categoryPreference;

    /**
     * 商圈偏好，如 大关=5, 运河上街=2
     */
    private Map<String, Integer> areaPreference;

    /**
     * 平均预算偏好
     */
    private Integer avgBudget;

    /**
     * 是否偏好高分
     */
    private Boolean preferHighScore;

    /**
     * 是否偏好优惠券
     */
    private Boolean preferCoupon;
}