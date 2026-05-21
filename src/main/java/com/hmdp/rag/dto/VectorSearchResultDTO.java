package com.hmdp.rag.dto;

import lombok.Data;

@Data
public class VectorSearchResultDTO {

    private Long chunkId;
    private String content;
    private Double score;
    private String bizType;
    private Long bizId;
}