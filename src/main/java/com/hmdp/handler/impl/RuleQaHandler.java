package com.hmdp.handler.impl;

import com.hmdp.dto.AiEvidenceDTO;
import com.hmdp.dto.request.AiChatRequest;
import com.hmdp.dto.memory.AiSessionContextDTO;
import com.hmdp.dto.response.AiChatResponse;
import com.hmdp.handler.AiChatHandler;
import com.hmdp.handler.HandlerUtils;
import com.hmdp.service.AiLlmService;
import com.hmdp.tool.RuleTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuleQaHandler implements AiChatHandler {

    private final RuleTool ruleTool;
    private final AiLlmService aiLlmService;
    private final HandlerUtils handlerUtils;

    @Override
    public AiChatResponse handle(AiChatRequest request, AiSessionContextDTO context) {
        String message = request.getMessage();

        // 1. 检索规则 evidence（Milvus 向量检索优先 + 关键词 fallback）
        List<AiEvidenceDTO> evidenceList = ruleTool.queryRuleEvidence(message);

        // 2. 提取命中标题
        List<String> hitTitles = evidenceList == null ? Collections.emptyList()
                : evidenceList.stream()
                .map(AiEvidenceDTO::getTitle)
                .filter(handlerUtils::isNotBlank)
                .collect(Collectors.toList());

        log.info("handleRuleQa question={}, evidenceCount={}, hitTitles={}",
                message,
                evidenceList == null ? 0 : evidenceList.size(),
                hitTitles);

        // 3. 无命中直接兜底
        if (evidenceList == null || evidenceList.isEmpty()) {
            return handlerUtils.buildTextResponse(
                    "暂时没有查到与你问题相关的平台规则，你可以换个更具体的问法，比如'优惠券过期了怎么办'或'秒杀券可以提现吗'。",
                    "RULE_QA",
                    context,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    true
            );
        }

        // 4. 构造 fallback 答案
        String fallbackAnswer = ruleTool.buildRuleFallbackAnswer(evidenceList);

        // 5. 基于 evidence 调大模型生成
        String answer = aiLlmService.answerWithEvidence(message, evidenceList, fallbackAnswer);

        boolean fallbackUsed = handlerUtils.isBlank(answer);
        if (fallbackUsed) {
            answer = fallbackAnswer;
        }

        log.info("handleRuleQa final answer={}, fallbackUsed={}", answer, fallbackUsed);
        // 6. 返回响应
        return handlerUtils.buildTextResponse(
                answer,
                "RULE_QA",
                context,
                Collections.singletonList("RULE_DOC"),
                hitTitles,
                fallbackUsed
        );
    }
}
