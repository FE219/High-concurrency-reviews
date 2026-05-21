package com.hmdp.rag.service;

public interface BlogVectorBuildService {

    /**
     * 全量构建 Blog 向量索引
     */
    void rebuildBlogVectorIndex();
}