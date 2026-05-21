package com.hmdp.rag.dto;

import com.hmdp.rag.enums.RagSourceType;
import lombok.Data;

@Data
public class RagContextDTO {

    /**
     * 知识源类型
     */
    private RagSourceType sourceType;

    /**
     * 检索到的上下文文本
     */
    private String contextText;

    /**
     * 额外说明
     */
    private String note;
}