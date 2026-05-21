package com.hmdp.tool;

import cn.hutool.core.collection.CollUtil;
import com.hmdp.dto.AiEvidenceDTO;
import com.hmdp.entity.Blog;
import com.hmdp.rag.service.VectorService;
import com.hmdp.service.IBlogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class BlogTool {

    @Resource
    private IBlogService blogService;

    @Resource
    private VectorService vectorService;

    /**
     * 查询某个店铺相关的探店笔记
     */
    public List<Blog> queryBlogsByShopId(Long shopId, int limit) {
        if (shopId == null) {
            return Collections.emptyList();
        }

        return blogService.query()
                .eq("shop_id", shopId)
                .orderByDesc("liked")
                .last("limit " + limit)
                .list();
    }

    public List<AiEvidenceDTO> queryShopBlogEvidence(Long shopId, String question, int limit) {
        // 1. 优先 Milvus 向量检索
        if (question != null && !question.trim().isEmpty()) {
            try {
                List<AiEvidenceDTO> vectorResult = vectorService.searchBlogByVector(question, shopId, limit);
                if (vectorResult != null && !vectorResult.isEmpty()) {
                    log.info("blog evidence hit by milvus vector, shopId={}, question={}, count={}",
                            shopId, question, vectorResult.size());
                    return vectorResult;
                }
            } catch (Exception e) {
                log.error("blog vector search error, fallback to db, shopId={}", shopId, e);
            }
        }

        // 2. fallback 数据库查询
        log.info("blog evidence fallback to db query, shopId={}", shopId);

        List<Blog> blogs = queryBlogsByShopId(shopId, limit);
        if (blogs == null || blogs.isEmpty()) {
            return Collections.emptyList();
        }

        return blogs.stream()
                .map(this::toEvidence)
                .collect(Collectors.toList());
    }

    private AiEvidenceDTO toEvidence(Blog blog) {
        AiEvidenceDTO evidence = new AiEvidenceDTO();
        evidence.setSourceType("BLOG");
        evidence.setSourceId(blog.getId());
        evidence.setTitle(blog.getTitle());
        evidence.setContent(truncate(blog.getContent(), 220));
        return evidence;
    }

    private String truncate(String content, int maxLen) {
        if (content == null) {
            return "";
        }
        content = content.trim();
        if (content.length() <= maxLen) {
            return content;
        }
        return content.substring(0, maxLen) + "...";
    }
}