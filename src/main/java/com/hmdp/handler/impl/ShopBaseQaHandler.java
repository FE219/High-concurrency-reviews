package com.hmdp.handler.impl;

import com.hmdp.dto.request.AiChatRequest;
import com.hmdp.dto.memory.AiSessionContextDTO;
import com.hmdp.dto.response.AiChatResponse;
import com.hmdp.enums.AiIntentType;
import com.hmdp.handler.AiChatHandler;
import com.hmdp.handler.HandlerUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ShopBaseQaHandler implements AiChatHandler {

    private final HandlerUtils handlerUtils;

    @Override
    public AiChatResponse handle(AiChatRequest request, AiSessionContextDTO context) {
        AiChatResponse response = new AiChatResponse();
        response.setIntent(AiIntentType.SHOP_BASE_QA.name());

        Long shopId = handlerUtils.resolveShopId(request, context);
        if (shopId == null) {
            response.setReply("请先告诉我你想了解哪家店，或者先从推荐卡片中选择一家店。");
            response.setSuggestions(buildShopBaseSuggestions());
            return response;
        }

        String defaultReply = "当前没有查到这家店更完整的基础信息，你可以尝试查看这家店的详情。";
        String reply = handlerUtils.generateRagReply(request.getMessage(), context, defaultReply);

        response.setReply(reply);
        response.setSuggestions(buildShopBaseSuggestions());
        return response;
    }

    private List<String> buildShopBaseSuggestions() {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("这家店营业到几点");
        suggestions.add("这家店人均多少");
        suggestions.add("这家店在哪");
        suggestions.add("查看这家店详情");
        return suggestions;
    }
}
