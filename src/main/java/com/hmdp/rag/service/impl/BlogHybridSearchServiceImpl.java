package com.hmdp.rag.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.entity.Blog;
import com.hmdp.rag.dto.BlogHybridResultDTO;
import com.hmdp.rag.dto.VectorSearchResultDTO;
import com.hmdp.rag.service.BlogHybridSearchService;
import com.hmdp.rag.service.BlogVectorSearchService;
import com.hmdp.service.IBlogService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

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

    @Override
    public List<BlogHybridResultDTO> searchByShop(String question, Long shopId, int topK) {
        if (shopId == null) {
            System.out.println("[RAG][BLOG] shopId为空，无法检索");
            return Collections.emptyList();
        }

        System.out.println("[RAG][BLOG] 当前shopId=" + shopId + ", 问题=" + question);

        Map<String, BlogHybridResultDTO> mergedMap = new HashMap<>();

        // 1. 向量检索优先
        List<VectorSearchResultDTO> vectorResults = blogVectorSearchService.searchByShop(question, shopId, topK);
        System.out.println("[RAG][BLOG] 向量命中数量=" + (vectorResults == null ? 0 : vectorResults.size()));

        if (CollUtil.isNotEmpty(vectorResults)) {
            for (VectorSearchResultDTO item : vectorResults) {
                BlogHybridResultDTO dto = new BlogHybridResultDTO();
                dto.setUniqueKey("BLOG_" + item.getChunkId());
                dto.setContent(item.getContent());
                dto.setSource("VECTOR");
                dto.setScore(100.0 + item.getScore() * 20);
                mergedMap.put(dto.getUniqueKey(), dto);
            }
        }

        // 2. 关键词补充
        List<String> keywords = extractBlogKeywords(question);
        System.out.println("[RAG][BLOG] 提取关键词=" + keywords);

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
                    .last("limit 5")
                    .list();

            System.out.println("[RAG][BLOG] 关键词命中数量=" + (blogList == null ? 0 : blogList.size()));

            if (CollUtil.isNotEmpty(blogList)) {
                for (int i = 0; i < blogList.size(); i++) {
                    Blog blog = blogList.get(i);

                    String uniqueKey = "BLOG_DOC_" + blog.getId();
                    String content = buildBlogText(blog);

                    BlogHybridResultDTO existing = mergedMap.get(uniqueKey);
                    if (existing != null) {
                        existing.setScore(existing.getScore() + 10);
                    } else {
                        BlogHybridResultDTO dto = new BlogHybridResultDTO();
                        dto.setUniqueKey(uniqueKey);
                        dto.setContent(content);
                        dto.setSource("KEYWORD");
                        dto.setScore(80.0 - i * 3);
                        mergedMap.put(uniqueKey, dto);
                    }
                }
            }
        }

        List<BlogHybridResultDTO> finalList = mergedMap.values().stream()
                .sorted(Comparator.comparing(BlogHybridResultDTO::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());

        System.out.println("[RAG][BLOG] 最终TopK数量=" + finalList.size());
        for (BlogHybridResultDTO item : finalList) {
            System.out.println("[RAG][BLOG] 命中结果 -> source=" + item.getSource()
                    + ", score=" + item.getScore()
                    + ", contentPreview=" + preview(item.getContent()));
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