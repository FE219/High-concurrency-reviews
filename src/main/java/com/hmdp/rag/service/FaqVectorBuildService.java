package com.hmdp.rag.service;

public interface FaqVectorBuildService {

    /**
     * 全量构建 FAQ 向量库
     */
    void rebuildFaqVectorIndex();
}