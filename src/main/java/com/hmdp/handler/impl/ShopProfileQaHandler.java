package com.hmdp.handler.impl;

import com.hmdp.dto.AiEvidenceDTO;
import com.hmdp.dto.request.AiChatRequest;
import com.hmdp.dto.memory.AiSessionContextDTO;
import com.hmdp.dto.response.AiChatResponse;
import com.hmdp.handler.AiChatHandler;
import com.hmdp.handler.HandlerUtils;
import com.hmdp.service.AiLlmService;
import com.hmdp.tool.BlogTool;
import com.hmdp.tool.ShopProfileTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopProfileQaHandler implements AiChatHandler {

    private final ShopProfileTool shopProfileTool;
    private final BlogTool blogTool;
    private final AiLlmService aiLlmService;
    private final HandlerUtils handlerUtils;

    @Override
    public AiChatResponse handle(AiChatRequest request, AiSessionContextDTO context) {
        String message = request.getMessage();

        Long shopId = context != null ? context.getLastShopId() : null;
        String shopName = context != null ? context.getLastShopName() : null;

        if (shopId == null) {
            return handlerUtils.buildTextResponse(
                    "你想了解哪家店呢？可以先告诉我店名，或者先点击推荐卡片里的'查看详情'。",
                    "SHOP_PROFILE_QA",
                    context,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    true
            );
        }

        List<AiEvidenceDTO> evidenceList = new ArrayList<>();

        // 1. 商户画像摘要
        AiEvidenceDTO profileEvidence = shopProfileTool.queryShopProfileEvidence(shopId, shopName);
        if (profileEvidence != null) {
            evidenceList.add(profileEvidence);
        }

        // 2. blog evidence：Milvus 向量检索优先 + DB fallback
        List<AiEvidenceDTO> blogEvidenceList = blogTool.queryShopBlogEvidence(shopId, message, 3);
        if (blogEvidenceList != null && !blogEvidenceList.isEmpty()) {
            evidenceList.addAll(blogEvidenceList);
        }

        List<String> knowledgeSources = evidenceList.stream()
                .map(AiEvidenceDTO::getSourceType)
                .filter(handlerUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());

        List<String> hitTitles = evidenceList.stream()
                .map(AiEvidenceDTO::getTitle)
                .filter(handlerUtils::isNotBlank)
                .collect(Collectors.toList());

        log.info("handleShopProfileQa question={}, shopId={}, shopName={}, evidenceCount={}, sources={}, titles={}",
                message, shopId, shopName,
                evidenceList.size(), knowledgeSources, hitTitles);

        if (evidenceList.isEmpty()) {
            String displayName = handlerUtils.isBlank(shopName) ? "这家店" : shopName;
            return handlerUtils.buildTextResponse(
                    displayName + " 暂时还没有足够的探店内容，我目前没法给你较准确的环境或体验判断。你也可以先看看店铺评分、详情和优惠券信息。",
                    "SHOP_PROFILE_QA",
                    context,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    true
            );
        }

        String fallbackAnswer = buildShopProfileFallbackAnswer(shopName, evidenceList);
        String answer = aiLlmService.answerWithEvidence(message, evidenceList, fallbackAnswer);

        boolean fallbackUsed = handlerUtils.isBlank(answer);
        if (fallbackUsed) {
            answer = fallbackAnswer;
        }

        return handlerUtils.buildTextResponse(
                answer,
                "SHOP_PROFILE_QA",
                context,
                knowledgeSources,
                hitTitles,
                fallbackUsed
        );
    }

    private String buildShopProfileFallbackAnswer(String shopName, List<AiEvidenceDTO> evidenceList) {
        String displayName = handlerUtils.isBlank(shopName) ? "这家店" : shopName;

        String profileSummary = null;
        for (AiEvidenceDTO evidence : evidenceList) {
            if ("SHOP_PROFILE".equals(evidence.getSourceType())
                    && handlerUtils.isNotBlank(evidence.getContent())) {
                profileSummary = evidence.getContent();
                break;
            }
        }

        if (handlerUtils.isNotBlank(profileSummary)) {
            return "结合现有探店内容看，" + displayName + "大致情况是：" + profileSummary;
        }

        return displayName + " 目前有一些用户探店反馈，但信息还不算特别完整。建议你结合店铺评分、优惠券和详情信息一起判断。";
    }
}
