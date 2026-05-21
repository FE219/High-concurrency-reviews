package com.hmdp.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class ShopProfileTagDTO {

    /**
     * 店铺ID
     */
    private Long shopId;

    /**
     * 场景标签
     * 如：约会、聚餐、亲子
     */
    private List<String> sceneTags;

    /**
     * 风格标签
     * 如：浪漫、环境好、精致、适合拍照
     */
    private List<String> styleTags;

    /**
     * 特征标签
     * 如：性价比高、服务好、菜品颜值高
     */
    private List<String> featureTags;

    /**
     * 摘要
     */
    private String summary;
}