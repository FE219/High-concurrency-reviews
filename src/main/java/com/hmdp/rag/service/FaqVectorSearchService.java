package com.hmdp.rag.service;


import com.hmdp.rag.dto.VectorSearchResultDTO;

import java.util.List;

public interface FaqVectorSearchService {

    /**
     * FAQ 向量检索
     */
    List<VectorSearchResultDTO> search(String question, int topK);
}