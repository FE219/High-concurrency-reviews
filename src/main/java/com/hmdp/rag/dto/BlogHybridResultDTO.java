package com.hmdp.rag.dto;

import lombok.Data;

@Data
public class BlogHybridResultDTO {

    /**
     * 唯一标识
     */
    private String uniqueKey;

    /**
     * 内容
     */
    private String content;

    /**
     * 来源：VECTOR / KEYWORD
     */
    private String source;

    /**
     * 综合分
     */
    private Double score;
}