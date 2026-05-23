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
public class VoucherRuleQaHandler implements AiChatHandler {

    private final HandlerUtils handlerUtils;

    @Override
    public AiChatResponse handle(AiChatRequest request, AiSessionContextDTO context) {
        AiChatResponse response = new AiChatResponse();
        response.setIntent(AiIntentType.VOUCHER_RULE_QA.name());

        Long shopId = handlerUtils.resolveShopId(request, context);
        if (shopId == null) {
            response.setReply("请先告诉我你想咨询哪家店的优惠券规则，或者先从推荐卡片中选择一家店。");
            response.setSuggestions(buildVoucherRuleSuggestions());
            return response;
        }

        String defaultReply = "当前没有查到这家店优惠券的详细规则说明，你可以先查看这家店的优惠券列表。";
        String reply = handlerUtils.generateRagReply(request.getMessage(), context, defaultReply);

        response.setReply(reply);
        response.setSuggestions(buildVoucherRuleSuggestions());
        return response;
    }

    private List<String> buildVoucherRuleSuggestions() {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("这张券怎么用");
        suggestions.add("这张券有什么限制");
        suggestions.add("这家店有什么优惠券");
        return suggestions;
    }
}
