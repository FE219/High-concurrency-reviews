package com.hmdp.rag.service.impl;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.AiKnowledgeChunk;
import com.hmdp.rag.dto.VectorSearchResultDTO;
import com.hmdp.rag.service.BlogVectorSearchService;
import com.hmdp.rag.service.EmbeddingService;
import com.hmdp.rag.util.SimilarityUtils;
import com.hmdp.service.IAiKnowledgeChunkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BlogVectorSearchServiceImpl implements BlogVectorSearchService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private IAiKnowledgeChunkService aiKnowledgeChunkService;

    @Resource
    private EmbeddingService embeddingService;

    @Override
    public List<VectorSearchResultDTO> searchByShop(String question, Long shopId, int topK) {
        if (shopId == null) {
            return new ArrayList<>();
        }

        List<Float> queryVector = embeddingService.embed(question);

        // 关键：只查当前店铺的 BLOG chunk
        List<AiKnowledgeChunk> chunks = aiKnowledgeChunkService.queryByBizTypeAndBizId("BLOG", shopId);

        if (chunks == null || chunks.isEmpty()) {
            return new ArrayList<>();
        }

        List<VectorSearchResultDTO> resultList = new ArrayList<>();

        for (AiKnowledgeChunk chunk : chunks) {
            try {
                List<Double> chunkVector = MAPPER.readValue(
                        chunk.getEmbeddingJson(),
                        new TypeReference<List<Double>>() {}
                );

                double score = SimilarityUtils.cosineSimilarity(queryVector, chunkVector);

                VectorSearchResultDTO dto = new VectorSearchResultDTO();
                dto.setChunkId(chunk.getId());
                dto.setContent(chunk.getContent());
                dto.setScore(score);
                dto.setBizType(chunk.getBizType());
                dto.setBizId(chunk.getBizId());
                resultList.add(dto);
            } catch (Exception e) {
                log.error("BLOG向量检索chunk解析失败 chunkId={}", chunk.getId(), e);
            }
        }

        return resultList.stream()
                .sorted(Comparator.comparing(VectorSearchResultDTO::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }
}