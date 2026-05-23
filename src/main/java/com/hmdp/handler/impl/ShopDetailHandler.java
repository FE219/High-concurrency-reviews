package com.hmdp.handler.impl;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.request.AiChatRequest;
import com.hmdp.dto.memory.AiSessionContextDTO;
import com.hmdp.dto.response.AiChatResponse;
import com.hmdp.dto.tool.ShopSimpleDTO;
import com.hmdp.enums.AiIntentType;
import com.hmdp.handler.AiChatHandler;
import com.hmdp.handler.HandlerUtils;
import com.hmdp.service.AiLlmService;
import com.hmdp.tool.ShopTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopDetailHandler implements AiChatHandler {

    private final ShopTool shopTool;
    private final AiLlmService aiLlmService;
    private final HandlerUtils handlerUtils;

    @Override
    public AiChatResponse handle(AiChatRequest request, AiSessionContextDTO context) {
        AiChatResponse response = new AiChatResponse();
        response.setIntent(AiIntentType.SHOP_DETAIL.name());

        Long shopId = handlerUtils.resolveShopId(request, context);
        if (shopId == null) {
            response.setReply("请先告诉我你想查看哪家店的详情，或者直接点击推荐卡片上的'查看详情'按钮。");
            response.setSuggestions(buildShopDetailSuggestions());
            return response;
        }

        ShopSimpleDTO shop = shopTool.getShopById(shopId);
        if (shop == null) {
            response.setReply("暂时没有查到这家店的详细信息。");
            response.setSuggestions(buildShopDetailSuggestions());
            return response;
        }

        // 更新上下文
        context.setLastShopId(shop.getShopId());
        context.setLastShopName(shop.getName());

        // 默认兜底文案
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(shop.getName()).append("】的详细信息如下：");

        if (shop.getScore() != null) {
            sb.append("评分").append(handlerUtils.formatMoney(shop.getScore())).append("分；");
        }
        if (shop.getAvgPrice() != null) {
            sb.append("人均").append(handlerUtils.formatMoney(shop.getAvgPrice())).append("元；");
        }
        if (StrUtil.isNotBlank(shop.getArea())) {
            sb.append("位于").append(shop.getArea()).append("；");
        }
        if (StrUtil.isNotBlank(shop.getAddress())) {
            sb.append("地址：").append(shop.getAddress()).append("；");
        }
        if (StrUtil.isNotBlank(shop.getOpenHours())) {
            sb.append("营业时间：").append(shop.getOpenHours()).append("；");
        }

        String defaultReply = sb.toString();

        // 通义千问润色
        try {
            String aiReply = aiLlmService.polishShopDetailReply(shop);
            if (aiReply != null && !aiReply.trim().isEmpty()) {
                response.setReply(aiReply);
            } else {
                response.setReply(defaultReply);
            }
        } catch (Exception e) {
            log.error("店铺详情AI润色失败", e);
            response.setReply(defaultReply);
        }

        response.setSuggestions(buildShopDetailSuggestions());
        return response;
    }

    private List<String> buildShopDetailSuggestions() {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("这家店有什么优惠券");
        suggestions.add("推荐附近类似店铺");
        suggestions.add("还有别的适合聚餐的店吗");
        return suggestions;
    }
}
