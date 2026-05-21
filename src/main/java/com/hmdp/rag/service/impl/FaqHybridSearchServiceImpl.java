package com.hmdp.rag.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;

import com.hmdp.dto.tool.RuleDocDTO;
import com.hmdp.rag.dto.FaqHybridResultDTO;
import com.hmdp.rag.dto.VectorSearchResultDTO;
import com.hmdp.rag.service.FaqHybridSearchService;
import com.hmdp.rag.service.FaqVectorSearchService;
import com.hmdp.tool.RuleTool;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FaqHybridSearchServiceImpl implements FaqHybridSearchService {

    @Resource
    private RuleTool ruleTool;

    @Resource
    private FaqVectorSearchService faqVectorSearchService;

    @Override
    public List<FaqHybridResultDTO> search(String question, int topK) {
        Map<String, FaqHybridResultDTO> mergedMap = new HashMap<>();

        // 1. 关键词检索
        List<RuleDocDTO> keywordDocs = ruleTool.searchRules(question);
        System.out.println("[RAG][FAQ] 关键词命中数量=" + (keywordDocs == null ? 0 : keywordDocs.size()));

        if (CollUtil.isNotEmpty(keywordDocs)) {
            for (int i = 0; i < keywordDocs.size(); i++) {
                RuleDocDTO doc = keywordDocs.get(i);

                FaqHybridResultDTO dto = new FaqHybridResultDTO();
                dto.setUniqueKey("RULE_" + doc.getId());
                dto.setTitle(doc.getTitle());
                dto.setContent(doc.getContent());
                dto.setSource("KEYWORD");
                dto.setScore(100.0 - i * 5);

                mergedMap.put(dto.getUniqueKey(), dto);
            }
        }

        // 2. 向量检索
        List<VectorSearchResultDTO> vectorDocs = faqVectorSearchService.search(question, topK);
        System.out.println("[RAG][FAQ] 向量命中数量=" + (vectorDocs == null ? 0 : vectorDocs.size()));

        if (CollUtil.isNotEmpty(vectorDocs)) {
            for (VectorSearchResultDTO vectorDoc : vectorDocs) {
                String uniqueKey = "RULE_" + vectorDoc.getBizId();

                FaqHybridResultDTO existing = mergedMap.get(uniqueKey);
                if (existing != null) {
                    existing.setScore(existing.getScore() + vectorDoc.getScore() * 20);
                } else {
                    FaqHybridResultDTO dto = new FaqHybridResultDTO();
                    dto.setUniqueKey(uniqueKey);
                    dto.setTitle("FAQ规则");
                    dto.setContent(extractFaqContent(vectorDoc.getContent()));
                    dto.setSource("VECTOR");
                    dto.setScore(80.0 + vectorDoc.getScore() * 20);
                    mergedMap.put(uniqueKey, dto);
                }
            }
        }

        List<FaqHybridResultDTO> finalList = mergedMap.values().stream()
                .sorted(Comparator.comparing(FaqHybridResultDTO::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());

        System.out.println("[RAG][FAQ] 最终TopK数量=" + finalList.size());
        for (FaqHybridResultDTO item : finalList) {
            System.out.println("[RAG][FAQ] 命中结果 -> source=" + item.getSource()
                    + ", score=" + item.getScore()
                    + ", title=" + item.getTitle());
        }

        return finalList;
    }

    @Override
    public String buildFaqContext(String question, int topK) {
        List<FaqHybridResultDTO> results = search(question, topK);
        if (CollUtil.isEmpty(results)) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (FaqHybridResultDTO item : results) {
            sb.append("【FAQ规则").append(index).append("】\n");
            if (StrUtil.isNotBlank(item.getTitle())) {
                sb.append("标题：").append(item.getTitle()).append("\n");
            }
            if (StrUtil.isNotBlank(item.getContent())) {
                sb.append("内容：").append(item.getContent()).append("\n");
            }
            sb.append("\n");
            index++;
        }
        return sb.toString();
    }

    /**
     * 从 FAQ 向量 chunk 内容中提取正文
     * 因为 FAQ chunk 存的时候可能是：
     * 标题：xxx
     * 关键词：xxx
     * 内容：xxx
     */
    private String extractFaqContent(String rawContent) {
        if (StrUtil.isBlank(rawContent)) {
            return "";
        }

        int idx = rawContent.indexOf("内容：");
        if (idx >= 0) {
            return rawContent.substring(idx + 3).trim();
        }
        return rawContent;
    }


}