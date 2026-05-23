package com.hmdp.rag.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.entity.Blog;
import com.hmdp.rag.dto.BlogHybridResultDTO;
import com.hmdp.rag.dto.VectorSearchResultDTO;
import com.hmdp.rag.service.BlogHybridSearchService;
import com.hmdp.rag.service.BlogVectorSearchService;
import com.hmdp.service.IBlogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BlogHybridSearchServiceImpl implements BlogHybridSearchService {

    @Resource
    private BlogVectorSearchService blogVectorSearchService;

    @Resource
    private IBlogService blogService;

    private String preview(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 50 ? content : content.substring(0, 50) + "...";
    }

    /**
     * RRF 融合常量，业界经验值 60
     */
    private static final double RRF_K = 60.0;

    @Override
    public List<BlogHybridResultDTO> searchByShop(String question, Long shopId, int topK) {
        if (shopId == null) {
            log.warn("[RAG][BLOG] shopId为空，无法检索");
            return Collections.emptyList();
        }

        log.info("[RAG][BLOG] 当前shopId={}, 问题={}", shopId, question);

        // 存储每个文档的 RRF 分数和来源标记
        Map<String, Double> rrfScoreMap = new HashMap<>();
        Map<String, String> sourceMap = new HashMap<>();
        Map<String, String> contentMap = new HashMap<>();

        // ========== 1. 向量检索 ==========
        List<VectorSearchResultDTO> vectorResults = blogVectorSearchService.searchByShop(question, shopId, 20);
        log.info("[RAG][BLOG] 向量命中数量={}", vectorResults == null ? 0 : vectorResults.size());

        if (CollUtil.isNotEmpty(vectorResults)) {
            for (int rank = 0; rank < vectorResults.size(); rank++) {
                VectorSearchResultDTO item = vectorResults.get(rank);
                String uniqueKey = "BLOG_" + item.getChunkId();

                double rrfScore = 1.0 / (RRF_K + rank + 1);
                rrfScoreMap.put(uniqueKey, rrfScore);
                sourceMap.put(uniqueKey, "VECTOR");
                contentMap.put(uniqueKey, item.getContent());
            }
        }

        // ========== 2. 关键词检索 ==========
        List<String> keywords = extractBlogKeywords(question);
        log.info("[RAG][BLOG] 提取关键词={}", keywords);

        if (CollUtil.isNotEmpty(keywords)) {
            List<Blog> blogList = blogService.query()
                    .eq("shop_id", shopId)
                    .and(wrapper -> {
                        boolean first = true;
                        for (String keyword : keywords) {
                            if (first) {
                                wrapper.like("title", keyword)
                                        .or()
                                        .like("content", keyword);
                                first = false;
                            } else {
                                wrapper.or().like("title", keyword)
                                        .or()
                                        .like("content", keyword);
                            }
                        }
                    })
                    .last("limit 20")
                    .list();

            log.info("[RAG][BLOG] 关键词命中数量={}", blogList == null ? 0 : blogList.size());

            if (CollUtil.isNotEmpty(blogList)) {
                for (int rank = 0; rank < blogList.size(); rank++) {
                    Blog blog = blogList.get(rank);
                    String uniqueKey = "BLOG_DOC_" + blog.getId();

                    double rrfScore = 1.0 / (RRF_K + rank + 1);
                    // 如果已被向量检索命中，累加 RRF 分数
                    rrfScoreMap.merge(uniqueKey, rrfScore, Double::sum);

                    // 来源标记：双路命中优先标记 VECTOR+KEYWORD
                    String existingSource = sourceMap.get(uniqueKey);
                    sourceMap.put(uniqueKey, existingSource == null ? "KEYWORD" : "VECTOR+KEYWORD");

                    // 内容：向量检索的结果更精准，优先保留
                    if (!contentMap.containsKey(uniqueKey)) {
                        contentMap.put(uniqueKey, buildBlogText(blog));
                    }
                }
            }
        }

        // ========== 3. 按 RRF 分数排序 ==========
        List<BlogHybridResultDTO> finalList = rrfScoreMap.entrySet().stream()
                .map(entry -> {
                    BlogHybridResultDTO dto = new BlogHybridResultDTO();
                    dto.setUniqueKey(entry.getKey());
                    dto.setScore(entry.getValue());
                    dto.setSource(sourceMap.getOrDefault(entry.getKey(), "UNKNOWN"));
                    dto.setContent(contentMap.getOrDefault(entry.getKey(), ""));
                    return dto;
                })
                .sorted(Comparator.comparing(BlogHybridResultDTO::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());

        log.info("[RAG][BLOG] RRF融合后TopK数量={}", finalList.size());
        for (BlogHybridResultDTO item : finalList) {
            log.info("[RAG][BLOG] 命中结果 -> source={}, rrfScore={:.6f}, contentPreview={}",
                    item.getSource(), item.getScore(), preview(item.getContent()));
        }

        return finalList;
    }

    @Override
    public String buildBlogContext(String question, Long shopId, int topK) {
        List<BlogHybridResultDTO> results = searchByShop(question, shopId, topK);
        if (CollUtil.isEmpty(results)) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (BlogHybridResultDTO item : results) {
            sb.append("【探店内容").append(index).append("】\n");
            sb.append(item.getContent()).append("\n\n");
            index++;
        }
        return sb.toString();
    }

    private List<String> extractBlogKeywords(String question) {
        List<String> list = new ArrayList<>();

        if (question.contains("约会")) list.add("约会");
        if (question.contains("环境")) list.add("环境");
        if (question.contains("浪漫")) list.add("浪漫");
        if (question.contains("拍照")) list.add("拍照");
        if (question.contains("值不值得")) list.add("推荐");
        if (question.contains("评价")) list.add("好吃");
        if (question.contains("氛围")) list.add("氛围");
        if (question.contains("聚餐")) list.add("聚餐");

        return list;
    }

    private String buildBlogText(Blog blog) {
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(blog.getTitle())) {
            sb.append("标题：").append(cleanText(blog.getTitle())).append("\n");
        }
        if (StrUtil.isNotBlank(blog.getContent())) {
            sb.append("内容：").append(cleanText(blog.getContent()));
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