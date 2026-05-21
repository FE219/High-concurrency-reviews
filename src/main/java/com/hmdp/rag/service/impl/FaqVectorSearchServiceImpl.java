package com.hmdp.rag.service.impl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.AiKnowledgeChunk;
import com.hmdp.rag.dto.VectorSearchResultDTO;
import com.hmdp.rag.service.EmbeddingService;
import com.hmdp.rag.service.FaqVectorSearchService;
import com.hmdp.rag.util.SimilarityUtils;
import com.hmdp.service.IAiKnowledgeChunkService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FaqVectorSearchServiceImpl implements FaqVectorSearchService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private IAiKnowledgeChunkService aiKnowledgeChunkService;

    @Resource
    private EmbeddingService embeddingService;

    @Override
    public List<VectorSearchResultDTO> search(String question, int topK) {
        List<Float> queryVector = embeddingService.embed(question);

        List<AiKnowledgeChunk> chunks = aiKnowledgeChunkService.queryByBizType("FAQ");
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
                e.printStackTrace();
            }
        }

        return resultList.stream()
                .sorted(Comparator.comparing(VectorSearchResultDTO::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }
}