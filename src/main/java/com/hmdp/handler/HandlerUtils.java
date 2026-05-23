package com.hmdp.handler;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.AiEvidenceDTO;
import com.hmdp.dto.memory.AiSessionContextDTO;
import com.hmdp.dto.request.AiChatRequest;
import com.hmdp.dto.response.AiChatResponse;
import com.hmdp.dto.tool.ShopSimpleDTO;
import com.hmdp.rag.dto.RagContextDTO;
import com.hmdp.rag.service.RagService;
import com.hmdp.service.AiLlmService;
import com.hmdp.tool.ShopTool;
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
public class HandlerUtils {

    private final ShopTool shopTool;
    private final RagService ragService;
    private final AiLlmService aiLlmService;

    /**
     * 解析店铺ID：
     * 1. 优先使用上下文中的 lastShopId
     * 2. 没有则尝试从消息里提取店名再搜索
     */
    public Long resolveShopId(AiChatRequest request, AiSessionContextDTO context) {
        // 1. 前端显式传了 shopId，优先使用
        if (request.getShopId() != null) {
            ShopSimpleDTO shop = shopTool.getShopById(request.getShopId());
            if (shop != null) {
                context.setLastShopId(shop.getShopId());
                context.setLastShopName(shop.getName());
                return shop.getShopId();
            }
        }

        // 2. 再从当前消息里提取店名
        String possibleShopName = extractPossibleShopName(request.getMessage());
        if (StrUtil.isNotBlank(possibleShopName)) {
            ShopSimpleDTO shop = shopTool.searchFirstByKeyword(possibleShopName);
            if (shop != null) {
                context.setLastShopId(shop.getShopId());
                context.setLastShopName(shop.getName());
                log.info("[resolveShopId] 使用当前消息店名={}, 解析shopId={}", possibleShopName, shop.getShopId());
                return shop.getShopId();
            }
        }

        // 3. 当前消息里没有明确店名，再回退到上下文
        if (context.getLastShopId() != null) {
            log.info("[resolveShopId] 使用历史上下文 shopId={}, shopName={}", context.getLastShopId(), context.getLastShopName());
            return context.getLastShopId();
        }

        // 4. 最后用 lastShopName 兜底
        if (StrUtil.isNotBlank(context.getLastShopName())) {
            ShopSimpleDTO shop = shopTool.searchFirstByKeyword(context.getLastShopName());
            if (shop != null) {
                context.setLastShopId(shop.getShopId());
                context.setLastShopName(shop.getName());
                log.info("[resolveShopId] 使用历史店名兜底 shopId={}", shop.getShopId());
                return shop.getShopId();
            }
        }
        log.info("[resolveShopId] 未解析到店铺");
        return null;
    }

    public String generateRagReply(String question, AiSessionContextDTO context, String defaultReply) {
        try {
            RagContextDTO ragContext = ragService.retrieveContext(question, context);
            if (ragContext == null || StrUtil.isBlank(ragContext.getContextText())) {
                log.info("[RAG] 未检索到上下文，使用默认回复");
                return defaultReply;
            }

            String aiReply = aiLlmService.generateRagAnswer(question, ragContext.getContextText());
            if (StrUtil.isNotBlank(aiReply)) {
                log.info("[RAG] 模型回答={}", aiReply);
                return aiReply;
            }
        } catch (Exception e) {
            log.error("[RAG] 模型生成失败 question={}", question, e);
        }

        log.warn("[RAG] 模型生成失败，回退默认回复");
        return defaultReply;
    }

    public String formatMoney(Double money) {
        if (money == null) {
            return "";
        }
        if (money == Math.floor(money)) {
            return String.valueOf(money.intValue());
        }
        return String.format("%.1f", money);
    }

    public boolean isNotBlank(String str) {
        return str != null && !str.trim().isEmpty();
    }

    public boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    public AiChatResponse buildTextResponse(String reply,
                                             String intent,
                                             AiSessionContextDTO context,
                                             List<String> knowledgeSources,
                                             List<String> hitTitles,
                                             boolean fallback) {
        AiChatResponse response = new AiChatResponse();
        response.setReply(reply);
        response.setIntent(intent);
        response.setSessionId(context != null ? context.getSessionId() : null);
        response.setKnowledgeSources(knowledgeSources);
        response.setHitTitles(hitTitles);
        response.setFallback(fallback);
        return response;
    }

    public String extractSearchKeyword(String message, AiSessionContextDTO context) {
        String possibleShopName = extractPossibleShopName(message);
        if (StrUtil.isNotBlank(possibleShopName)) {
            return possibleShopName;
        }
        if (StrUtil.isNotBlank(context.getCategoryKeyword())) {
            return context.getCategoryKeyword();
        }
        return message;
    }

    /**
     * 简单识别常见品牌/店名关键词
     */
    public String extractPossibleShopName(String message) {
        if (StrUtil.isBlank(message)) {
            return null;
        }

        if (message.contains("海底捞")) {
            return "海底捞";
        }
        if (message.contains("103茶餐厅")) {
            return "103茶餐厅";
        }
        if (message.contains("新白鹿")) {
            return "新白鹿";
        }
        if (message.contains("Mamala")) {
            return "Mamala";
        }
        if (message.contains("浅草屋")) {
            return "浅草屋";
        }
        if (message.contains("开乐迪")) {
            return "开乐迪";
        }
        if (message.contains("INLOVE")) {
            return "INLOVE";
        }
        if (message.contains("星聚会")) {
            return "星聚会";
        }

        return null;
    }

    public String buildSearchReason(ShopSimpleDTO shop) {
        List<String> reasons = new ArrayList<>();

        if (shop.getScore() != null && shop.getScore() >= 4.5) {
            reasons.add("评分较高");
        }
        if (shop.getAvgPrice() != null) {
            reasons.add("人均约" + formatMoney(shop.getAvgPrice()) + "元");
        }
        if (StrUtil.isNotBlank(shop.getArea())) {
            reasons.add("位于" + shop.getArea());
        }

        if (reasons.isEmpty()) {
            reasons.add("与关键词匹配");
        }
        return String.join("、", reasons);
    }
}
