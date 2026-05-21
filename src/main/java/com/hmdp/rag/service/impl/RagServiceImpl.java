package com.hmdp.rag.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.hmdp.dto.memory.AiSessionContextDTO;
import com.hmdp.dto.tool.ShopSimpleDTO;
import com.hmdp.dto.tool.VoucherSimpleDTO;
import com.hmdp.rag.dto.RagContextDTO;
import com.hmdp.rag.dto.VectorSearchResultDTO;
import com.hmdp.rag.enums.RagSourceType;
import com.hmdp.rag.service.*;
import com.hmdp.tool.CouponTool;
import com.hmdp.tool.RuleTool;
import com.hmdp.tool.ShopProfileTool;
import com.hmdp.tool.ShopTool;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagServiceImpl implements RagService {

    @Resource
    private BlogVectorSearchService blogVectorSearchService;

    @Resource
    private RagQueryRouter ragQueryRouter;

    @Resource
    private RuleTool ruleTool;

    @Resource
    private ShopProfileTool shopProfileTool;

    @Resource
    private ShopTool shopTool;

    @Resource
    private CouponTool couponTool;

    @Resource
    private FaqVectorSearchService faqVectorSearchService;

    @Resource
    private FaqHybridSearchService faqHybridSearchService;

    @Resource
    private BlogHybridSearchService blogHybridSearchService;

    @Override
    public RagContextDTO retrieveContext(String question, AiSessionContextDTO context) {
        RagSourceType sourceType = ragQueryRouter.route(question);

        RagContextDTO dto = new RagContextDTO();
        dto.setSourceType(sourceType);

        switch (sourceType) {
            case FAQ_RULE:
                dto.setContextText(buildFaqRuleContext(question));
                break;
            case SHOP_BLOG:
                dto.setContextText(buildShopBlogContext(question, context));
                break;
            case VOUCHER_RULE:
                dto.setContextText(buildVoucherRuleContext(context));
                break;
            case SHOP_BASE:
                dto.setContextText(buildShopBaseContext(context));
                break;
            default:
                dto.setContextText(null);
                dto.setNote("未匹配到知识源");
        }

        System.out.println("[RAG] 最终知识源=" + dto.getSourceType());
        System.out.println("[RAG] 最终上下文长度=" + (dto.getContextText() == null ? 0 : dto.getContextText().length()));
        if (dto.getContextText() != null) {
            System.out.println("[RAG] 最终上下文预览=" + preview(dto.getContextText()));
        }
        return dto;
    }

    private String preview(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }

    private String buildFaqRuleContext(String question) {
        return faqHybridSearchService.buildFaqContext(question, 3);
    }

    private String buildShopBlogContext(String question, AiSessionContextDTO context) {
        if (context == null || context.getLastShopId() == null) {
            return null;
        }

        return blogHybridSearchService.buildBlogContext(question, context.getLastShopId(), 3);
    }

    private String buildVoucherRuleContext(AiSessionContextDTO context) {
        if (context == null || context.getLastShopId() == null) {
            return null;
        }

        List<VoucherSimpleDTO> coupons = couponTool.queryShopCoupons(context.getLastShopId());
        if (CollUtil.isEmpty(coupons)) {
            return null;
        }

        return coupons.stream()
                .limit(3)
                .map(c -> "券名：" + c.getTitle()
                        + "\n副标题：" + c.getSubTitle()
                        + "\n到手价：" + c.getPayValue() + "元"
                        + "\n抵扣金额：" + c.getActualValue() + "元"
                        + "\n规则说明：" + c.getRules())
                .collect(Collectors.joining("\n\n"));
    }

    private String buildShopBaseContext(AiSessionContextDTO context) {
        if (context == null || context.getLastShopId() == null) {
            return null;
        }

        ShopSimpleDTO shop = shopTool.getShopById(context.getLastShopId());
        if (shop == null) {
            return null;
        }

        return "店铺名称：" + shop.getName()
                + "\n评分：" + shop.getScore()
                + "\n人均消费：" + shop.getAvgPrice() + "元"
                + "\n商圈：" + shop.getArea()
                + "\n地址：" + shop.getAddress()
                + "\n营业时间：" + shop.getOpenHours()
                + "\n距离：" + (shop.getDistance() == null ? "未知" : shop.getDistance() + "米");
    }

    private String trimContext(String context, int maxLen) {
        if (context == null) {
            return null;
        }
        return context.length() <= maxLen ? context : context.substring(0, maxLen);
    }
}