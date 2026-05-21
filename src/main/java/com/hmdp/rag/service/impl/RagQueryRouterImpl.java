package com.hmdp.rag.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hmdp.rag.enums.RagSourceType;
import com.hmdp.rag.service.RagQueryRouter;
import org.springframework.stereotype.Service;

@Service
public class RagQueryRouterImpl implements RagQueryRouter {

    @Override
    public RagSourceType route(String question) {
        if (StrUtil.isBlank(question)) {
            System.out.println("[RAG] 问题为空，路由=UNKNOWN");
            return RagSourceType.UNKNOWN;
        }

        RagSourceType result;

        if (containsAny(question, "退款", "过期", "规则", "叠加", "秒杀", "使用规则")) {
            result = RagSourceType.FAQ_RULE;
        } else if (containsAny(question, "适合约会", "环境怎么样", "用户评价", "评价怎么样", "值不值得去", "有什么特点")) {
            result = RagSourceType.SHOP_BLOG;
        } else if (containsAny(question, "这张券怎么用", "券规则", "代金券怎么用", "优惠券怎么用", "这个券怎么用", "这个代金券规则")) {
            result = RagSourceType.VOUCHER_RULE;
        } else if (containsAny(question, "营业到几点", "营业时间", "在哪", "地址", "人均多少", "哪个商圈", "在哪里")) {
            result = RagSourceType.SHOP_BASE;
        } else {
            result = RagSourceType.UNKNOWN;
        }

        System.out.println("[RAG] 问题=" + question);
        System.out.println("[RAG] 路由=" + result.name());

        return result;
    }

    private boolean containsAny(String text, String... keywords) {
        if (StrUtil.isBlank(text) || keywords == null || keywords.length == 0) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}