package com.hmdp.dto.memory;

import lombok.Data;

@Data
public class AiSessionContextDTO {

    private String sessionId;
    private Long userId;
    private String lastIntent;

    /**
     * 当前对话中的品类关键词，如 火锅/日料/KTV
     */
    private String categoryKeyword;

    /**
     * 预算，如 100 表示100元以内
     */
    private Integer budget;

    /**
     * 场景，如 约会/聚餐/亲子
     */
    private String scene;

    /**
     * 纬度
     */
    private Double lat;

    /**
     * 经度
     */
    private Double lon;

    /**
     * 最近一次识别出的店铺id
     */
    private Long lastShopId;

    /**
     * 最近一次提到的店铺名称
     */
    private String lastShopName;

    /**
     * 最低评分要求，例如 4.5
     */
    private Double minScore;

    /**
     * 是否偏好环境好
     */
    private Boolean preferGoodEnvironment;

    /**
     * 是否偏好高性价比
     */
    private Boolean preferHighValue;
}