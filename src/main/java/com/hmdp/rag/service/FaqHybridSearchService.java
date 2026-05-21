package com.hmdp.rag.service;

import com.hmdp.rag.dto.FaqHybridResultDTO;

import java.util.List;

public interface FaqHybridSearchService {

    /**
     * FAQ 混合检索
     */
    List<FaqHybridResultDTO> search(String question, int topK);

    /**
     * FAQ 混合检索后，拼成上下文文本
     */
    String buildFaqContext(String question, int topK);
}