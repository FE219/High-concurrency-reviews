package com.hmdp.rag.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.hmdp.dto.memory.AiRuleDoc;
import com.hmdp.entity.AiKnowledgeChunk;
import com.hmdp.rag.service.EmbeddingService;
import com.hmdp.rag.service.FaqVectorBuildService;
import com.hmdp.service.IAiKnowledgeChunkService;
import com.hmdp.service.IAiRuleDocService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class FaqVectorBuildServiceImpl implements FaqVectorBuildService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private IAiRuleDocService aiRuleDocService;

    @Resource
    private IAiKnowledgeChunkService aiKnowledgeChunkService;

    @Resource
    private EmbeddingService embeddingService;

    @Override
    public void rebuildFaqVectorIndex() {
        // 1. 删除原有 FAQ chunk
        aiKnowledgeChunkService.remove(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AiKnowledgeChunk>()
                        .eq("biz_type", "FAQ")
        );

        // 2. 查所有启用的 FAQ
        List<AiRuleDoc> docs = aiRuleDocService.lambdaQuery()
                .eq(AiRuleDoc::getStatus, 1)
                .list();

        if (docs == null || docs.isEmpty()) {
            return;
        }

        for (AiRuleDoc doc : docs) {
            String content = buildFaqContent(doc);

            List<Float> embedding = embeddingService.embed(content);

            AiKnowledgeChunk chunk = new AiKnowledgeChunk();
            chunk.setDocId(doc.getId());
            chunk.setBizType("FAQ");
            chunk.setBizId(doc.getId());
            chunk.setChunkIndex(0);
            chunk.setContent(content);
            chunk.setStatus(1);

            try {
                chunk.setEmbeddingJson(MAPPER.writeValueAsString(embedding));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

            aiKnowledgeChunkService.save(chunk);
        }
    }

    private String buildFaqContent(AiRuleDoc doc) {
        StringBuilder sb = new StringBuilder();
        sb.append("标题：").append(doc.getTitle()).append("\n");
        if (doc.getKeywords() != null) {
            sb.append("关键词：").append(doc.getKeywords()).append("\n");
        }
        sb.append("内容：").append(doc.getContent());
        return sb.toString();
    }
}