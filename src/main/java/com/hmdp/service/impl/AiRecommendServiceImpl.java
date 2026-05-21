package com.hmdp.service.impl;


import cn.hutool.core.collection.CollUtil;
import com.hmdp.dto.memory.AiSessionContextDTO;
import com.hmdp.dto.request.AiChatRequest;
import com.hmdp.dto.response.AiChatResponse;
import com.hmdp.dto.response.RecommendationItemDTO;
import com.hmdp.dto.response.ShopProfileTagDTO;
import com.hmdp.dto.response.UserProfilePreferenceDTO;
import com.hmdp.dto.tool.ShopSimpleDTO;
import com.hmdp.dto.tool.VoucherSimpleDTO;
import com.hmdp.enums.AiIntentType;
import com.hmdp.service.AiLlmService;
import com.hmdp.service.AiRecommendService;
import com.hmdp.service.ShopProfileService;
import com.hmdp.service.UserProfileService;
import com.hmdp.tool.CouponTool;
import com.hmdp.tool.ShopTool;
import cn.hutool.core.util.StrUtil;
import lombok.Data;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AiRecommendServiceImpl implements AiRecommendService {

    /**
     * tb_shop_type 对应：
     * 1 = 美食
     * 2 = KTV
     */
    private static final int TYPE_FOOD = 1;
    private static final int TYPE_KTV = 2;

    @Resource
    private AiLlmService aiLlmService;

    @Resource
    private CouponTool couponTool;

    @Resource
    private ShopTool shopTool;

    @Resource
    private ShopProfileService shopProfileService;

    @Resource
    private UserProfileService userProfileService;

    @Override
    public AiChatResponse recommend(AiChatRequest request, AiSessionContextDTO context) {
        AiChatResponse response = new AiChatResponse();
        response.setIntent(AiIntentType.RECOMMEND.name());

        // 1. 检查定位
        if (context.getLat() == null || context.getLon() == null) {
            response.setReply("请先授权定位，或者把你的位置告诉我，这样我才能帮你推荐附近店铺。");
            response.setSuggestions(buildLocationMissingSuggestions());
            return response;
        }

        // 2. 根据用户问题判断“大类”
        Integer typeId = resolveMainTypeId(context.getCategoryKeyword(), request.getMessage());
        if (typeId == null) {
            response.setReply("你想找哪一类店呢？比如美食、KTV，或者直接说火锅、日料、烧烤。");
            response.setSuggestions(buildCategorySuggestions());
            return response;
        }

        // 3. 查询附近店铺
        List<ShopSimpleDTO> shops = shopTool.searchNearbyByType(
                typeId,
                context.getLon(), // x = 经度
                context.getLat(), // y = 纬度
                1
        );

        if (CollUtil.isEmpty(shops)) {
            response.setReply("附近暂时没有找到相关店铺，你可以换个条件试试。");
            response.setSuggestions(buildCategorySuggestions());
            return response;
        }

        // 4. 如果是美食类里的细分词，需要二次过滤
        String subCategoryKeyword = resolveSubCategoryKeyword(context.getCategoryKeyword(), request.getMessage());
        if (StrUtil.isNotBlank(subCategoryKeyword) && TYPE_FOOD == typeId) {
            List<ShopSimpleDTO> filtered = shopTool.filterBySubCategoryKeyword(shops, subCategoryKeyword);
            if (CollUtil.isNotEmpty(filtered)) {
                shops = filtered;
            }
        }

        // 5. 预算过滤
        if (context.getBudget() != null) {
            shops = shops.stream()
                    .filter(shop -> shop.getAvgPrice() == null || shop.getAvgPrice() <= context.getBudget())
                    .collect(Collectors.toList());
        }

        if (context.getMinScore() != null) {
            shops = shops.stream()
                    .filter(shop -> shop.getScore() != null && shop.getScore() >= context.getMinScore())
                    .collect(Collectors.toList());
        }

        if (CollUtil.isEmpty(shops)) {
            response.setReply("附近有相关店铺，但没有找到符合你预算的。你可以适当放宽预算试试看。");
            response.setSuggestions(buildBudgetSuggestions());
            return response;
        }

        if (context.getUserId() != null) {
            if (context.getCategoryKeyword() != null) {
                userProfileService.recordCategoryPreference(context.getUserId(), context.getCategoryKeyword());
            }
            if (context.getBudget() != null) {
                userProfileService.recordBudgetPreference(context.getUserId(), context.getBudget());
            }
            if (context.getMinScore() != null && context.getMinScore() >= 4.5) {
                userProfileService.recordPreferHighScore(context.getUserId());
            }
        }

        if (context.getUserId() != null && !shops.isEmpty() && shops.get(0).getArea() != null) {
            userProfileService.recordAreaPreference(context.getUserId(), shops.get(0).getArea());
        }
        UserProfilePreferenceDTO userProfile;
        if (context.getUserId() != null) {
            userProfile = userProfileService.getUserProfile(context.getUserId());
        } else {
            userProfile = null;
        }

// 7. 先计算每家店的推荐分
        List<ScoredShop> scoredShops = shops.stream().map(shop -> {
            List<VoucherSimpleDTO> coupons = couponTool.queryShopCoupons(shop.getShopId());
            boolean hasCoupon = coupons != null && !coupons.isEmpty();

            ShopProfileTagDTO shopProfileTag = shopProfileService.getShopProfileTag(shop.getShopId(), shop.getName());

            double recommendScore = calculateRecommendScore(shop, context, userProfile, hasCoupon, shopProfileTag);

            ScoredShop scoredShop = new ScoredShop();
            scoredShop.setShop(shop);
            scoredShop.setCoupons(coupons);
            scoredShop.setHasCoupon(hasCoupon);
            scoredShop.setRecommendScore(recommendScore);
            scoredShop.setShopProfileTag(shopProfileTag);
            return scoredShop;
        }).collect(Collectors.toList());

// 8. 按推荐分排序
        scoredShops = scoredShops.stream()
                .sorted(Comparator.comparing(ScoredShop::getRecommendScore).reversed())
                .limit(3)
                .collect(Collectors.toList());

// 9. 转推荐结果
        List<RecommendationItemDTO> recommendations = scoredShops.stream().map(scored -> {
            ShopSimpleDTO shop = scored.getShop();

            RecommendationItemDTO dto = new RecommendationItemDTO();
            dto.setShopId(shop.getShopId());
            dto.setShopName(shop.getName());
            dto.setScore(shop.getScore());
            dto.setDistance(shop.getDistance());
            dto.setDistanceText(formatDistance(shop.getDistance()));
            dto.setAvgPrice(shop.getAvgPrice());
            dto.setAvgPriceText(formatAvgPrice(shop.getAvgPrice()));
            dto.setArea(shop.getArea());
            dto.setOpenHours(shop.getOpenHours());
            dto.setReason(buildReason(shop, context, scored.getShopProfileTag()));
            dto.setRecommendScore(scored.getRecommendScore());

            fillCouponInfo(dto, scored.getCoupons(), scored.isHasCoupon());

            return dto;
        }).collect(Collectors.toList());

        // 8. 回写最近一个店铺，方便“这家店有什么券”
        if (CollUtil.isNotEmpty(shops)) {
            context.setLastShopId(shops.get(0).getShopId());
            context.setLastShopName(shops.get(0).getName());
        }


        String defaultReply = buildReply(context, recommendations, subCategoryKeyword, typeId);

        try {
            String aiReply = aiLlmService.polishRecommendReply(request.getMessage(), recommendations);
            if (aiReply != null && !aiReply.trim().isEmpty()) {
                response.setReply(aiReply);
            } else {
                response.setReply(defaultReply);
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setReply(defaultReply);
        }

        if (context.getUserId() != null) {
            if (context.getCategoryKeyword() != null) {
                userProfileService.recordCategoryPreference(context.getUserId(), context.getCategoryKeyword());
            }
            if (context.getBudget() != null) {
                userProfileService.recordBudgetPreference(context.getUserId(), context.getBudget());
            }
            if (context.getMinScore() != null && context.getMinScore() >= 4.5) {
                userProfileService.recordPreferHighScore(context.getUserId());
            }
            if (!recommendations.isEmpty() && recommendations.get(0).getHasCoupon() != null && recommendations.get(0).getHasCoupon()) {
                userProfileService.recordPreferCoupon(context.getUserId());
            }
            if (!recommendations.isEmpty() && recommendations.get(0).getArea() != null) {
                userProfileService.recordAreaPreference(context.getUserId(), recommendations.get(0).getArea());
            }
        }
        response.setRecommendations(recommendations);
        response.setSuggestions(buildRecommendSuggestions(context, typeId));
        return response;

    }

    private double calculateRecommendScore(ShopSimpleDTO shop,
                                           AiSessionContextDTO context,
                                           UserProfilePreferenceDTO userProfile,
                                           boolean hasCoupon,
                                           ShopProfileTagDTO shopProfileTag) {
        double total = 0.0;

        // 1. 评分分：评分越高越高
        if (shop.getScore() != null) {
            total += shop.getScore() * 20; // 例如 4.9 -> 98 分
        }

        // 2. 距离分：越近越高
        if (shop.getDistance() != null) {
            if (shop.getDistance() <= 500) {
                total += 30;
            } else if (shop.getDistance() <= 1000) {
                total += 20;
            } else if (shop.getDistance() <= 3000) {
                total += 10;
            } else {
                total += 5;
            }
        }

        // 3. 预算匹配分：越接近用户预算越高
        if (context.getBudget() != null && shop.getAvgPrice() != null) {
            double diff = Math.abs(shop.getAvgPrice() - context.getBudget());
            if (diff <= 20) {
                total += 20;
            } else if (diff <= 50) {
                total += 10;
            } else if (shop.getAvgPrice() <= context.getBudget()) {
                total += 5;
            } else {
                total -= 5;
            }
        }

        // 4. 优惠分：有券加分
        if (hasCoupon) {
            total += 15;
        }

        // 5. 用户画像加权
        if (userProfile != null) {

            // 5.1 高分偏好
            if (Boolean.TRUE.equals(userProfile.getPreferHighScore())
                    && shop.getScore() != null
                    && shop.getScore() >= 4.5) {
                total += 10;
            }

            // 5.2 优惠偏好
            if (Boolean.TRUE.equals(userProfile.getPreferCoupon()) && hasCoupon) {
                total += 10;
            }

            // 5.3 商圈偏好
            if (shop.getArea() != null && userProfile.getAreaPreference() != null) {
                Integer areaScore = userProfile.getAreaPreference().get(shop.getArea());
                if (areaScore != null) {
                    total += Math.min(areaScore, 15); // 上限15，避免太夸张
                }
            }

            // 5.4 预算偏好
            if (userProfile.getAvgBudget() != null && shop.getAvgPrice() != null) {
                double diff = Math.abs(shop.getAvgPrice() - userProfile.getAvgBudget());
                if (diff <= 20) {
                    total += 8;
                } else if (diff <= 50) {
                    total += 4;
                }
            }

            // 5.5 品类偏好
            if (context.getCategoryKeyword() != null && userProfile.getCategoryPreference() != null) {
                Integer categoryScore = userProfile.getCategoryPreference().get(context.getCategoryKeyword());
                if (categoryScore != null) {
                    total += Math.min(categoryScore, 15); // 上限15
                }
            }

        }
        // 6. 商户画像标签匹配分
        if (shopProfileTag != null) {

            // 6.1 场景匹配
            if (context.getScene() != null && shopProfileTag.getSceneTags() != null) {
                if (shopProfileTag.getSceneTags().contains(context.getScene())) {
                    total += 20;
                }
            }

            // 6.2 环境偏好
            if (Boolean.TRUE.equals(context.getPreferGoodEnvironment())
                    && shopProfileTag.getStyleTags() != null) {
                if (shopProfileTag.getStyleTags().contains("环境好")
                        || shopProfileTag.getStyleTags().contains("浪漫")
                        || shopProfileTag.getStyleTags().contains("精致")
                        || shopProfileTag.getStyleTags().contains("氛围好")) {
                    total += 15;
                }
            }

            // 6.3 性价比偏好
            if (Boolean.TRUE.equals(context.getPreferHighValue())
                    && shopProfileTag.getFeatureTags() != null) {
                if (shopProfileTag.getFeatureTags().contains("性价比高")
                        || shopProfileTag.getFeatureTags().contains("划算")) {
                    total += 15;
                }
            }
        }

        return total;
    }

    private void fillCouponInfo(RecommendationItemDTO dto, List<VoucherSimpleDTO> coupons, boolean hasCoupon) {
        if (!hasCoupon || coupons == null || coupons.isEmpty()) {
            dto.setHasCoupon(false);
            dto.setCouponCount(0);
            dto.setCouponSummary("暂无优惠券");
            return;
        }

        dto.setHasCoupon(true);
        dto.setCouponCount(coupons.size());

        VoucherSimpleDTO first = coupons.get(0);
        if (first.getTitle() != null && first.getPayValue() != null) {
            dto.setCouponSummary(first.getTitle() + "，到手价" + formatMoney(first.getPayValue()) + "元");
        } else if (first.getTitle() != null) {
            dto.setCouponSummary(first.getTitle());
        } else {
            dto.setCouponSummary("有" + coupons.size() + "张优惠券");
        }
    }

    private String formatDistance(Double distance) {
        if (distance == null) {
            return "";
        }
        if (distance < 1000) {
            return String.format("%.0f米", distance);
        }
        return String.format("%.1f公里", distance / 1000);
    }

    private String formatAvgPrice(Double avgPrice) {
        if (avgPrice == null) {
            return "人均未知";
        }
        return "人均" + formatMoney(avgPrice) + "元";
    }

    private String formatMoney(Double money) {
        if (money == null) {
            return "";
        }
        if (money == Math.floor(money)) {
            return String.valueOf(money.intValue());
        }
        return String.format("%.1f", money);
    }

    /**
     * 解析大类：
     * - KTV -> 2
     * - 火锅/日料/烧烤/茶餐厅/咖啡/奶茶/美食 -> 1
     */
    private Integer resolveMainTypeId(String categoryKeyword, String message) {
        String text = mergeText(categoryKeyword, message);

        if (StrUtil.isBlank(text)) {
            return null;
        }

        if (containsAny(text, "ktv", "KTV", "唱歌")) {
            return TYPE_KTV;
        }

        if (containsAny(text,
                "美食", "吃饭", "餐厅", "火锅", "日料", "寿司", "刺身",
                "烧烤", "烤肉", "茶餐厅", "冰厅", "咖啡", "奶茶", "小吃", "川菜", "西餐")) {
            return TYPE_FOOD;
        }

        return null;
    }

    /**
     * 解析美食类细分关键词
     */
    private String resolveSubCategoryKeyword(String categoryKeyword, String message) {
        String text = mergeText(categoryKeyword, message);

        if (StrUtil.isBlank(text)) {
            return null;
        }

        if (containsAny(text, "火锅")) {
            return "火锅";
        }
        if (containsAny(text, "日料", "寿司", "刺身")) {
            return "日料";
        }
        if (containsAny(text, "烧烤", "烤肉")) {
            return "烧烤";
        }
        if (containsAny(text, "茶餐厅", "冰厅")) {
            return "茶餐厅";
        }
        if (containsAny(text, "咖啡")) {
            return "咖啡";
        }
        if (containsAny(text, "奶茶")) {
            return "奶茶";
        }

        return null;
    }

    private String buildReason(ShopSimpleDTO shop,
                               AiSessionContextDTO context,
                               ShopProfileTagDTO shopProfileTag) {
        List<String> reasons = new ArrayList<>();

        if (shop.getScore() != null && shop.getScore() >= 4.5) {
            reasons.add("评分较高");
        }
        if (shop.getDistance() != null && shop.getDistance() <= 1000) {
            reasons.add("距离较近");
        }
        if (context.getBudget() != null && shop.getAvgPrice() != null && shop.getAvgPrice() <= context.getBudget()) {
            reasons.add("符合预算");
        }
        if (StrUtil.isNotBlank(shop.getArea())) {
            reasons.add("位于" + shop.getArea());
        }
        if (StrUtil.isNotBlank(context.getScene())) {
            reasons.add("适合" + context.getScene());
        }

        if (reasons.isEmpty()) {
            reasons.add("综合条件匹配");
        }
        if (shopProfileTag != null) {
            if (context.getScene() != null
                    && shopProfileTag.getSceneTags() != null
                    && shopProfileTag.getSceneTags().contains(context.getScene())) {
                reasons.add("适合" + context.getScene());
            }

            if (Boolean.TRUE.equals(context.getPreferGoodEnvironment())
                    && shopProfileTag.getStyleTags() != null
                    && !shopProfileTag.getStyleTags().isEmpty()) {
                reasons.add("环境氛围不错");
            }

            if (Boolean.TRUE.equals(context.getPreferHighValue())
                    && shopProfileTag.getFeatureTags() != null
                    && (shopProfileTag.getFeatureTags().contains("性价比高")
                    || shopProfileTag.getFeatureTags().contains("划算"))) {
                reasons.add("性价比不错");
            }
        }
        return String.join("、", reasons);
    }

    private String buildReply(AiSessionContextDTO context,
                              List<RecommendationItemDTO> recommendations,
                              String subCategoryKeyword,
                              Integer typeId) {
        String categoryText;
        if (StrUtil.isNotBlank(subCategoryKeyword)) {
            categoryText = subCategoryKeyword;
        } else if (typeId != null && typeId == TYPE_KTV) {
            categoryText = "KTV";
        } else {
            categoryText = "店铺";
        }

        StringBuilder sb = new StringBuilder("我为你推荐了几家附近不错的").append(categoryText);

        if (context.getBudget() != null) {
            sb.append("，尽量控制在人均").append(context.getBudget()).append("元以内");
        }

        if (context.getMinScore() != null) {
            sb.append("，并筛选了评分").append(context.getMinScore()).append("分以上的店");
        }

        sb.append("。");
        return sb.toString();
    }

    private List<String> buildRecommendSuggestions(AiSessionContextDTO context, Integer typeId) {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("查看这家店有什么优惠券");
        suggestions.add("换成人均100以内");
        suggestions.add("再推荐几家");
        suggestions.add("只看评分4.5以上的");

        if (typeId != null && typeId == TYPE_FOOD) {
            suggestions.add("只看离我近一点的美食店");
        } else if (typeId != null && typeId == TYPE_KTV) {
            suggestions.add("推荐离我更近的KTV");
        }
        return suggestions;
    }

    private List<String> buildLocationMissingSuggestions() {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("授权定位");
        suggestions.add("推荐附近火锅");
        suggestions.add("推荐附近KTV");
        return suggestions;
    }

    private List<String> buildCategorySuggestions() {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("推荐附近火锅");
        suggestions.add("推荐附近日料");
        suggestions.add("推荐附近KTV");
        suggestions.add("推荐附近茶餐厅");
        return suggestions;
    }

    private List<String> buildBudgetSuggestions() {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("换成人均150以内");
        suggestions.add("换成人均200以内");
        suggestions.add("只看离我近一点的");
        return suggestions;
    }

    private String mergeText(String categoryKeyword, String message) {
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(categoryKeyword)) {
            sb.append(categoryKeyword).append(" ");
        }
        if (StrUtil.isNotBlank(message)) {
            sb.append(message);
        }
        return sb.toString();
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

    @Data
    private static class ScoredShop {
        private ShopSimpleDTO shop;
        private List<VoucherSimpleDTO> coupons;
        private boolean hasCoupon;
        private double recommendScore;
        private ShopProfileTagDTO shopProfileTag;
    }
}
