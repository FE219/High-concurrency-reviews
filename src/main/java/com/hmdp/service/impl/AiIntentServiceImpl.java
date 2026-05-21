package com.hmdp.service.impl;


import com.hmdp.enums.AiIntentType;
import com.hmdp.service.AiIntentService;
import org.springframework.stereotype.Service;

@Service
public class AiIntentServiceImpl implements AiIntentService {

    @Override
    public AiIntentType detectIntent(String message) {
        if (message == null || message.trim().isEmpty()) {
            return AiIntentType.UNKNOWN;
        }

        String msg = message.trim();

        if (containsAny(msg, "退款", "过期", "使用规则", "秒杀规则", "团购规则")) {
            return AiIntentType.RULE_QA;
        }

        if (containsAny(msg, "优惠券", "团购券", "秒杀券", "有啥券", "有什么券")) {
            return AiIntentType.COUPON_QUERY;
        }

        if (containsAny(msg, "推荐", "附近有什么", "适合", "人均", "评分高", "离我近", "附近")) {
            return AiIntentType.RECOMMEND;
        }

        if (containsAny(msg, "查一下", "搜一下", "有没有", "店铺", "商家")) {
            return AiIntentType.SHOP_SEARCH;
        }

        return AiIntentType.UNKNOWN;
    }

    private boolean containsAny(String msg, String... keywords) {
        for (String keyword : keywords) {
            if (msg.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}