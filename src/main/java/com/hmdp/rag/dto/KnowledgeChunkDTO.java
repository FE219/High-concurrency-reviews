package com.hmdp.rag.dto;

import lombok.Data;

import java.util.List;

@Data
public class KnowledgeChunkDTO {

    private Long id;
    private Long docId;
    private String bizType;
    private Long bizId;
    private Integer chunkIndex;
    private String content;
    private List<Double> embedding;
}