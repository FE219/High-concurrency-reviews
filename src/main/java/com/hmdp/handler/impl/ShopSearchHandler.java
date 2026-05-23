package com.hmdp.handler.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.request.AiChatRequest;
import com.hmdp.dto.memory.AiSessionContextDTO;
import com.hmdp.dto.response.AiChatResponse;
import com.hmdp.dto.response.RecommendationItemDTO;
import com.hmdp.dto.tool.ShopSimpleDTO;
import com.hmdp.enums.AiIntentType;
import com.hmdp.handler.AiChatHandler;
import com.hmdp.handler.HandlerUtils;
import com.hmdp.tool.ShopTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ShopSearchHandler implements AiChatHandler {

    private final ShopTool shopTool;
    private final HandlerUtils handlerUtils;

    @Override
    public AiChatResponse handle(AiChatRequest request, AiSessionContextDTO context) {
        AiChatResponse response = new AiChatResponse();
        response.setIntent(AiIntentType.SHOP_SEARCH.name());

        String keyword = handlerUtils.extractSearchKeyword(request.getMessage(), context);
        if (StrUtil.isBlank(keyword)) {
            response.setReply("你可以告诉我想找什么店，比如'海底捞'、'火锅店'、'附近KTV'。");
            response.setSuggestions(buildSearchSuggestions());
            return response;
        }

        List<ShopSimpleDTO> shops = shopTool.searchByKeyword(keyword, 1);
        if (CollUtil.isEmpty(shops)) {
            response.setReply("暂时没有找到相关店铺，你可以换个关键词试试。");
            response.setSuggestions(buildSearchSuggestions());
            return response;
        }

        List<RecommendationItemDTO> recommendations = shops.stream()
                .limit(3)
                .map(shop -> {
                    RecommendationItemDTO dto = new RecommendationItemDTO();
                    dto.setShopId(shop.getShopId());
                    dto.setShopName(shop.getName());
                    dto.setScore(shop.getScore());
                    dto.setDistance(shop.getDistance());
                    dto.setAvgPrice(shop.getAvgPrice());
                    dto.setArea(shop.getArea());
                    dto.setOpenHours(shop.getOpenHours());
                    dto.setReason(handlerUtils.buildSearchReason(shop));
                    return dto;
                })
                .collect(Collectors.toList());

        // 回写最近一个店铺
        ShopSimpleDTO first = shops.get(0);
        context.setLastShopId(first.getShopId());
        context.setLastShopName(first.getName());

        response.setReply("我帮你找到几家相关店铺，你可以先看看。");
        response.setRecommendations(recommendations);
        response.setSuggestions(buildSearchSuggestions());
        return response;
    }

    private List<String> buildSearchSuggestions() {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("查看这家店有什么优惠券");
        suggestions.add("推荐附近类似店铺");
        suggestions.add("只看评分高一点的");
        return suggestions;
    }
}
