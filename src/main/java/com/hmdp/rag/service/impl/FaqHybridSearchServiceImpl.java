package com.hmdp.rag.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;

import com.hmdp.dto.tool.RuleDocDTO;
import com.hmdp.rag.dto.FaqHybridResultDTO;
import com.hmdp.rag.dto.VectorSearchResultDTO;
import com.hmdp.rag.service.FaqHybridSearchService;
import com.hmdp.rag.service.FaqVectorSearchService;
import com.hmdp.tool.RuleTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FaqHybridSearchServiceImpl implements FaqHybridSearchService {

    @Resource
    private RuleTool ruleTool;

    @Resource
    private FaqVectorSearchService faqVectorSearchService;

    /**
     * RRF 融合常量，业界经验值 60
     */
    private static final double RRF_K = 60.0;

    @Override
    public List<FaqHybridResultDTO> search(String question, int topK) {
        // 存储每个文档的 RRF 分数和元数据
        Map<String, Double> rrfScoreMap = new HashMap<>();
        Map<String, String> sourceMap = new HashMap<>();
        Map<String, String> titleMap = new HashMap<>();
        Map<String, String> contentMap = new HashMap<>();

        // ========== 1. 关键词检索 ==========
        List<RuleDocDTO> keywordDocs = ruleTool.searchRules(question);
        log.info("[RAG][FAQ] 关键词命中数量={}", keywordDocs == null ? 0 : keywordDocs.size());

        if (CollUtil.isNotEmpty(keywordDocs)) {
            for (int rank = 0; rank < keywordDocs.size(); rank++) {
                RuleDocDTO doc = keywordDocs.get(rank);
                String uniqueKey = "RULE_" + doc.getId();

                double rrfScore = 1.0 / (RRF_K + rank + 1);
                rrfScoreMap.put(uniqueKey, rrfScore);
                sourceMap.put(uniqueKey, "KEYWORD");
                titleMap.put(uniqueKey, doc.getTitle());
                contentMap.put(uniqueKey, doc.getContent());
            }
        }

        // ========== 2. 向量检索 ==========
        List<VectorSearchResultDTO> vectorDocs = faqVectorSearchService.search(question, 20);
        log.info("[RAG][FAQ] 向量命中数量={}", vectorDocs == null ? 0 : vectorDocs.size());

        if (CollUtil.isNotEmpty(vectorDocs)) {
            for (int rank = 0; rank < vectorDocs.size(); rank++) {
                VectorSearchResultDTO vectorDoc = vectorDocs.get(rank);
                String uniqueKey = "RULE_" + vectorDoc.getBizId();

                double rrfScore = 1.0 / (RRF_K + rank + 1);
                rrfScoreMap.merge(uniqueKey, rrfScore, Double::sum);

                // 来源标记：双路命中优先标记
                String existingSource = sourceMap.get(uniqueKey);
                sourceMap.put(uniqueKey, existingSource == null ? "VECTOR" : "KEYWORD+VECTOR");

                // 关键词检索的内容通常更完整，优先保留
                if (!titleMap.containsKey(uniqueKey)) {
                    titleMap.put(uniqueKey, "FAQ规则");
                }
                if (!contentMap.containsKey(uniqueKey)) {
                    contentMap.put(uniqueKey, extractFaqContent(vectorDoc.getContent()));
                }
            }
        }

        // ========== 3. 按 RRF 分数排序 ==========
        List<FaqHybridResultDTO> finalList = rrfScoreMap.entrySet().stream()
                .map(entry -> {
                    FaqHybridResultDTO dto = new FaqHybridResultDTO();
                    dto.setUniqueKey(entry.getKey());
                    dto.setScore(entry.getValue());
                    dto.setSource(sourceMap.getOrDefault(entry.getKey(), "UNKNOWN"));
                    dto.setTitle(titleMap.getOrDefault(entry.getKey(), ""));
                    dto.setContent(contentMap.getOrDefault(entry.getKey(), ""));
                    return dto;
                })
                .sorted(Comparator.comparing(FaqHybridResultDTO::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());

        log.info("[RAG][FAQ] RRF融合后TopK数量={}", finalList.size());
        for (FaqHybridResultDTO item : finalList) {
            log.info("[RAG][FAQ] 命中结果 -> source={}, rrfScore={:.6f}, title={}",
                    item.getSource(), item.getScore(), item.getTitle());
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