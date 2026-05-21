package com.hmdp.tool;

import cn.hutool.core.collection.CollUtil;
import com.hmdp.dto.AiEvidenceDTO;
import com.hmdp.dto.memory.AiRuleDoc;
import com.hmdp.dto.tool.RuleDocDTO;
import com.hmdp.rag.service.VectorService;
import com.hmdp.service.IAiRuleDocService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RuleTool {

    @Resource
    private IAiRuleDocService aiRuleDocService;


    @Resource
    private VectorService vectorService;

    /**
     * 根据问题搜索规则
     */
    public List<RuleDocDTO> searchRules(String query) {
        List<AiRuleDoc> docs = aiRuleDocService.searchRules(query);
        if (CollUtil.isEmpty(docs)) {
            return Collections.emptyList();
        }

        return docs.stream().map(this::toDTO).collect(Collectors.toList());
    }

    private RuleDocDTO toDTO(AiRuleDoc doc) {
        RuleDocDTO dto = new RuleDocDTO();
        dto.setId(doc.getId());
        dto.setTitle(doc.getTitle());
        dto.setContent(doc.getContent());
        dto.setCategory(doc.getCategory());
        return dto;
    }

    public String answerRuleQuestion(String userQuestion) {
        List<AiEvidenceDTO> evidenceList = queryRuleEvidence(userQuestion);
        if (evidenceList == null || evidenceList.isEmpty()) {
            return "暂时没有查到相关的平台规则信息。";
        }
        return buildRuleFallbackAnswer(evidenceList);
    }

    /**
     * 新增：RAG用的规则证据检索
     */
    public List<AiEvidenceDTO> queryRuleEvidence(String userQuestion) {
        try {
            // 1. 优先 Milvus 向量检索
            List<AiEvidenceDTO> vectorResult = vectorService.searchFaqByVector(userQuestion, 3);
            if (vectorResult != null && !vectorResult.isEmpty()) {
                log.info("queryRuleEvidence hit by milvus vector, question={}, count={}",
                        userQuestion, vectorResult.size());
                return vectorResult;
            }

            // 2. fallback 关键词检索
            log.info("queryRuleEvidence fallback to keyword search, question={}", userQuestion);
            List<AiRuleDoc> docs = searchRuleDocs(userQuestion);
            if (docs == null || docs.isEmpty()) {
                return Collections.emptyList();
            }

            return docs.stream()
                    .limit(3)
                    .map(this::toEvidence)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("queryRuleEvidence error, question={}", userQuestion, e);
            return Collections.emptyList();
        }
    }

    /**
     * 将FAQ文档转为统一 evidence DTO
     */
    private AiEvidenceDTO toEvidence(AiRuleDoc doc) {
        AiEvidenceDTO evidence = new AiEvidenceDTO();
        evidence.setSourceType("RULE_DOC");
        evidence.setSourceId(doc.getId());
        evidence.setTitle(doc.getTitle());
        evidence.setContent(doc.getContent());
        return evidence;
    }

    /**
     * 规则问答 fallback：模型失败时仍可直接输出
     */
    public String buildRuleFallbackAnswer(List<AiEvidenceDTO> evidenceList) {
        if (evidenceList == null || evidenceList.isEmpty()) {
            return "暂时没有查到相关的平台规则信息。";
        }

        StringBuilder sb = new StringBuilder("我帮你查到这些相关规则信息：\n");
        for (AiEvidenceDTO evidence : evidenceList) {
            if (evidence.getTitle() != null && !evidence.getTitle().trim().isEmpty()) {
                sb.append("【").append(evidence.getTitle()).append("】");
            }
            if (evidence.getContent() != null && !evidence.getContent().trim().isEmpty()) {
                sb.append(evidence.getContent());
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 关键：FAQ 检索逻辑
     * 这里优先复用你现在已有的“关键词命中增强版”逻辑
     */
    private List<AiRuleDoc> searchRuleDocs(String userQuestion) {
        return aiRuleDocService.searchRules(userQuestion);
    }

    /**
     * 简单关键词提取
     * 如果你当前已有更好的提取逻辑，请直接替换
     */
    private Set<String> extractKeywords(String userQuestion) {
        if (userQuestion == null || userQuestion.trim().isEmpty()) {
            return Collections.emptySet();
        }

        String text = userQuestion.trim()
                .replace("？", " ")
                .replace("?", " ")
                .replace("，", " ")
                .replace(",", " ")
                .replace("。", " ")
                .replace("！", " ")
                .replace("!", " ");

        String[] arr = text.split("\\s+");

        Set<String> keywords = new LinkedHashSet<>();
        for (String word : arr) {
            if (word == null) {
                continue;
            }
            word = word.trim();
            if (word.length() < 2) {
                continue;
            }
            if (isStopWord(word)) {
                continue;
            }
            keywords.add(word);
        }

        // 你也可以补一些规则类关键词增强
        if (text.contains("退款")) {
            keywords.add("退款");
        }
        if (text.contains("过期")) {
            keywords.add("过期");
        }
        if (text.contains("优惠券") || text.contains("券")) {
            keywords.add("优惠券");
        }
        if (text.contains("秒杀")) {
            keywords.add("秒杀");
        }
        if (text.contains("使用")) {
            keywords.add("使用");
        }

        return keywords;
    }

    /**
     * FAQ文档匹配分
     */
    private int calcMatchScore(AiRuleDoc doc, Set<String> keywords) {
        if (doc == null || keywords == null || keywords.isEmpty()) {
            return 0;
        }

        String title = safe(doc.getTitle());
        String content = safe(doc.getContent());

        int score = 0;
        for (String keyword : keywords) {
            if (title.contains(keyword)) {
                score += 3;
            }
            if (content.contains(keyword)) {
                score += 1;
            }
        }
        return score;
    }

    private boolean isStopWord(String word) {
        return "什么".equals(word)
                || "怎么".equals(word)
                || "可以".equals(word)
                || "请问".equals(word)
                || "一下".equals(word)
                || "这个".equals(word)
                || "那个".equals(word)
                || "一下子".equals(word);
    }

    private String safe(String str) {
        return str == null ? "" : str;
    }
}