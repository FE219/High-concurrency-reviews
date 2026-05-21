package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_ai_knowledge_chunk")
public class AiKnowledgeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long docId;

    /**
     * FAQ / BLOG / VOUCHER / SHOP
     */
    private String bizType;

    /**
     * FAQ 的话这里可存规则ID
     */
    private Long bizId;

    private Integer chunkIndex;

    private String content;

    /**
     * 向量json字符串
     */
    private String embeddingJson;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}