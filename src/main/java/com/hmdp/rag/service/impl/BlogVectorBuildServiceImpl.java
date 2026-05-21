package com.hmdp.rag.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.AiKnowledgeChunk;
import com.hmdp.entity.Blog;
import com.hmdp.rag.service.BlogVectorBuildService;
import com.hmdp.rag.service.EmbeddingService;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IAiKnowledgeChunkService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class BlogVectorBuildServiceImpl implements BlogVectorBuildService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private IBlogService blogService;

    @Resource
    private IAiKnowledgeChunkService aiKnowledgeChunkService;

    @Resource
    private EmbeddingService embeddingService;

    @Override
    public void rebuildBlogVectorIndex() {
        // 1. 删除原 BLOG 向量数据
        aiKnowledgeChunkService.remove(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AiKnowledgeChunk>()
                        .eq("biz_type", "BLOG")
        );

        // 2. 读取所有 blog
        List<Blog> blogs = blogService.lambdaQuery().list();
        if (blogs == null || blogs.isEmpty()) {
            return;
        }

        for (Blog blog : blogs) {
            String content = buildBlogContent(blog);

            // 可选：这里后面可以做切片，目前先每篇 blog 作为一个 chunk
            List<Float> embedding = embeddingService.embed(content);

            AiKnowledgeChunk chunk = new AiKnowledgeChunk();
            chunk.setDocId(blog.getId());
            chunk.setBizType("BLOG");
            chunk.setBizId(blog.getShopId()); // 用 shopId 作为业务主键
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

    private String buildBlogContent(Blog blog) {
        StringBuilder sb = new StringBuilder();

        if (blog.getTitle() != null) {
            sb.append("探店标题：").append(cleanText(blog.getTitle())).append("\n");
        }
        if (blog.getContent() != null) {
            sb.append("探店内容：").append(cleanText(blog.getContent())).append("\n");
        }

        return sb.toString();
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("<br\\s*/?>", " ")
                .replaceAll("\\\\n", " ")
                .replaceAll("\n", " ")
                .replaceAll("\r", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}