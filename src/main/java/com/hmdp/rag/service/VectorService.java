package com.hmdp.rag.service;


import com.hmdp.dto.AiEvidenceDTO;

import java.util.List;

public interface VectorService {

    /**
     * 创建 FAQ 向量集合
     */
    void createFaqCollectionIfNotExists();

    /**
     * 删除 FAQ 向量集合
     */
    void dropFaqCollection();

    /**
     * 同步 FAQ 文档到 Milvus
     */
    void syncFaqDocumentsToVectorStore();

    /**
     * FAQ 向量检索
     */
    List<AiEvidenceDTO> searchFaqByVector(String question, int topK);


    // BLOG
    void createBlogCollectionIfNotExists();

    void dropBlogCollection();

    void syncBlogDocumentsToVectorStore();

    List<AiEvidenceDTO> searchBlogByVector(String question, Long shopId, int topK);

}