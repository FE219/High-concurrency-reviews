package com.hmdp.handler.impl;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.request.AiChatRequest;
import com.hmdp.dto.memory.AiSessionContextDTO;
import com.hmdp.dto.response.AiChatResponse;
import com.hmdp.dto.tool.VoucherSimpleDTO;
import com.hmdp.enums.AiIntentType;
import com.hmdp.handler.AiChatHandler;
import com.hmdp.handler.HandlerUtils;
import com.hmdp.service.AiLlmService;
import com.hmdp.service.UserProfileService;
import com.hmdp.tool.CouponTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponQueryHandler implements AiChatHandler {

    private final CouponTool couponTool;
    private final UserProfileService userProfileService;
    private final AiLlmService aiLlmService;
    private final HandlerUtils handlerUtils;

    @Override
    public AiChatResponse handle(AiChatRequest request, AiSessionContextDTO context) {
        AiChatResponse response = new AiChatResponse();
        response.setIntent(AiIntentType.COUPON_QUERY.name());

        Long shopId = handlerUtils.resolveShopId(request, context);

        if (shopId == null) {
            response.setReply("请先告诉我你想查询哪家店的优惠券，比如'海底捞有什么券？'，或者直接点击推荐卡片上的'查优惠券'按钮。");
            response.setSuggestions(buildCouponSuggestions());
            return response;
        }

        List<VoucherSimpleDTO> coupons = couponTool.queryShopCoupons(shopId);
        String shopName = StrUtil.isBlank(context.getLastShopName()) ? "这家店" : context.getLastShopName();

        if (coupons == null || coupons.isEmpty()) {
            response.setReply(shopName + " 当前没有查到可用优惠券。");
            response.setSuggestions(buildCouponSuggestions());
            return response;
        }

        // 默认兜底文案
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(shopName).append("】当前有以下优惠券：");

        for (VoucherSimpleDTO coupon : coupons) {
            sb.append("【").append(coupon.getTitle()).append("】");

            if (coupon.getPayValue() != null && coupon.getActualValue() != null) {
                sb.append("到手价")
                        .append(handlerUtils.formatMoney(coupon.getPayValue()))
                        .append("元，可抵")
                        .append(handlerUtils.formatMoney(coupon.getActualValue()))
                        .append("元");
            } else if (coupon.getPayValue() != null) {
                sb.append("到手价").append(handlerUtils.formatMoney(coupon.getPayValue())).append("元");
            }

            if (Boolean.TRUE.equals(coupon.getSeckill())) {
                sb.append("（秒杀券）");
            }

            if (coupon.getSubTitle() != null && !coupon.getSubTitle().trim().isEmpty()) {
                sb.append("，").append(coupon.getSubTitle());
            }

            sb.append("；");
        }

        if (context.getUserId() != null) {
            userProfileService.recordPreferCoupon(context.getUserId());
        }
        String defaultReply = sb.toString();

        // 通义千问润色
        try {
            String aiReply = aiLlmService.polishCouponReply(shopName, coupons);
            if (aiReply != null && !aiReply.trim().isEmpty()) {
                response.setReply(aiReply);
            } else {
                response.setReply(defaultReply);
            }
        } catch (Exception e) {
            log.error("优惠券AI润色失败", e);
            response.setReply(defaultReply);
        }

        response.setSuggestions(buildCouponSuggestions());
        return response;
    }

    private List<String> buildCouponSuggestions() {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("还有别的优惠吗");
        suggestions.add("推荐附近更划算的店");
        suggestions.add("再找一家类似的店");
        return suggestions;
    }
}
