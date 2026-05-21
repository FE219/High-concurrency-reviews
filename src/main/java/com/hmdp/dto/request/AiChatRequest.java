package com.hmdp.dto.request;

import lombok.Data;

@Data
public class AiChatRequest {
    private String sessionId;
    private Long userId;
    private String message;

    /**
     * 纬度
     */
    private Double lat;

    /**
     * 经度
     */
    private Double lon;

    /**
     * 前端点击推荐卡片时，直接传入店铺ID
     */
    private Long shopId;
}