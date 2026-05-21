package com.hmdp.rag.dto;

import lombok.Data;

import java.util.List;

@Data
public class RagDebugInfoDTO {

    /**
     * 用户问题
     */
    private String question;

    /**
     * 路由结果
     */
    private String sourceType;

    /**
     * 检索命中的摘要信息
     */
    private List<String> retrievedChunks;

    /**
     * 最终拼接上下文
     */
    private String finalContext;

    /**
     * 最终回答
     */
    private String finalAnswer;
}