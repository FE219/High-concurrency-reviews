package com.hmdp.rag.dto;

import lombok.Data;

@Data
public class FaqHybridResultDTO {

    /**
     * 唯一标识，可用 ruleId 或 chunkId
     */
    private String uniqueKey;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 来源：KEYWORD / VECTOR
     */
    private String source;

    /**
     * 综合分
     */
    private Double score;
}