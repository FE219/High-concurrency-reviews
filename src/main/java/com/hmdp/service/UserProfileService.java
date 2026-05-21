package com.hmdp.service;

import com.hmdp.dto.response.UserProfilePreferenceDTO;

public interface UserProfileService {

    /**
     * 获取用户画像
     */
    UserProfilePreferenceDTO getUserProfile(Long userId);

    /**
     * 记录品类偏好
     */
    void recordCategoryPreference(Long userId, String category);

    /**
     * 记录商圈偏好
     */
    void recordAreaPreference(Long userId, String area);

    /**
     * 记录预算偏好
     */
    void recordBudgetPreference(Long userId, Integer budget);

    /**
     * 记录偏好高分
     */
    void recordPreferHighScore(Long userId);

    /**
     * 记录偏好优惠券
     */
    void recordPreferCoupon(Long userId);
}