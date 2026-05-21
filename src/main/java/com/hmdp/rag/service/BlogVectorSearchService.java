package com.hmdp.rag.service;

import com.hmdp.rag.dto.VectorSearchResultDTO;

import java.util.List;

public interface BlogVectorSearchService {

    /**
     * 按店铺范围检索 Blog 向量
     */
    List<VectorSearchResultDTO> searchByShop(String question, Long shopId, int topK);
}