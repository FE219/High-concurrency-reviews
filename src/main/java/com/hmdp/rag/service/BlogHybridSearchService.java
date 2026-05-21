package com.hmdp.rag.service;

import com.hmdp.rag.dto.BlogHybridResultDTO;

import java.util.List;

public interface BlogHybridSearchService {

    /**
     * 在指定店铺范围内做 Blog 混合检索
     */
    List<BlogHybridResultDTO> searchByShop(String question, Long shopId, int topK);

    /**
     * 生成 Blog 上下文
     */
    String buildBlogContext(String question, Long shopId, int topK);
}