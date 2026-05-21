package com.hmdp.rag.service;

import java.util.List;

public interface EmbeddingService {

    /**
     * 生成单条文本向量
     */
    List<Float> embed(String text);
}