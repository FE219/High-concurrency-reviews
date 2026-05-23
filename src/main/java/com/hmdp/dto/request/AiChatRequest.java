package com.hmdp.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class AiChatRequest {
    @Size(max = 36, message = "sessionId长度不能超过36位")
    private String sessionId;

    private Long userId;

    @NotBlank(message = "消息不能为空")
    @Size(max = 500, message = "消息长度不能超过500字")
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
