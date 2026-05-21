package com.hmdp.service;


import com.hmdp.dto.response.ShopProfileTagDTO;

public interface ShopProfileService {

    /**
     * 获取店铺画像标签
     */
    ShopProfileTagDTO getShopProfileTag(Long shopId, String shopName);

    /**
     * 刷新 / 生成店铺画像标签
     */
    ShopProfileTagDTO rebuildShopProfileTag(Long shopId, String shopName);
}