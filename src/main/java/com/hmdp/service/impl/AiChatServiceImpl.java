package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.constant.AiRedisKeyConstants;
import com.hmdp.dto.memory.AiSessionContextDTO;
import com.hmdp.dto.request.AiChatRequest;
import com.hmdp.dto.response.AiChatResponse;
import com.hmdp.dto.tool.ShopSimpleDTO;
import com.hmdp.enums.AiIntentType;
import com.hmdp.handler.AiChatHandler;
import com.hmdp.handler.HandlerUtils;
import com.hmdp.handler.impl.CouponQueryHandler;
import com.hmdp.handler.impl.RecommendHandler;
import com.hmdp.handler.impl.RuleQaHandler;
import com.hmdp.handler.impl.ShopBaseQaHandler;
import com.hmdp.handler.impl.ShopDetailHandler;
import com.hmdp.handler.impl.ShopProfileQaHandler;
import com.hmdp.handler.impl.ShopSearchHandler;
import com.hmdp.handler.impl.VoucherRuleQaHandler;
import com.hmdp.service.AiChatService;
import com.hmdp.tool.ShopTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AiChatServiceImpl implements AiChatService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopTool shopTool;

    @Resource
    private HandlerUtils handlerUtils;

    // Handlers
    @Resource
    private RecommendHandler recommendHandler;

    @Resource
    private CouponQueryHandler couponQueryHandler;

    @Resource
    private ShopSearchHandler shopSearchHandler;

    @Resource
    private ShopDetailHandler shopDetailHandler;

    @Resource
    private RuleQaHandler ruleQaHandler;

    @Resource
    private ShopProfileQaHandler shopProfileQaHandler;

    @Resource
    private VoucherRuleQaHandler voucherRuleQaHandler;

    @Resource
    private ShopBaseQaHandler shopBaseQaHandler;

    private final Map<AiIntentType, AiChatHandler> handlerMap = new EnumMap<>(AiIntentType.class);

    @PostConstruct
    public void initHandlers() {
        handlerMap.put(AiIntentType.RECOMMEND, recommendHandler);
        handlerMap.put(AiIntentType.COUPON_QUERY, couponQueryHandler);
        handlerMap.put(AiIntentType.SHOP_SEARCH, shopSearchHandler);
        handlerMap.put(AiIntentType.SHOP_DETAIL, shopDetailHandler);
        handlerMap.put(AiIntentType.RULE_QA, ruleQaHandler);
        handlerMap.put(AiIntentType.SHOP_PROFILE_QA, shopProfileQaHandler);
        handlerMap.put(AiIntentType.VOUCHER_RULE_QA, voucherRuleQaHandler);
        handlerMap.put(AiIntentType.SHOP_BASE_QA, shopBaseQaHandler);
    }

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        // 1. 基础校验
        if (request == null || StrUtil.isBlank(request.getMessage())) {
            AiChatResponse response = new AiChatResponse();
            response.setIntent(AiIntentType.UNKNOWN.name());
            response.setReply("请输入你想咨询的问题。");
            response.setSuggestions(buildDefaultSuggestions());
            return response;
        }

        // 2. sessionId 兜底
        if (StrUtil.isBlank(request.getSessionId())) {
            request.setSessionId(UUID.randomUUID().toString());
        }

        // 3. 获取会话上下文
        AiSessionContextDTO context = getContext(request.getSessionId());
        if (context == null) {
            context = new AiSessionContextDTO();
            context.setSessionId(request.getSessionId());
            context.setUserId(request.getUserId());
        }

        // 4. 合并本轮请求信息
        mergeRequestToContext(request, context);

        // 5. 识别意图
        AiIntentType intent = detectIntent(request);
        context.setLastIntent(intent.name());

        // 6. 路由处理
        AiChatResponse response;
        AiChatHandler handler = handlerMap.get(intent);
        if (handler != null) {
            response = handler.handle(request, context);
        } else {
            response = buildDefaultResponse();
        }

        // 7. 保存上下文
        saveContext(request.getSessionId(), context);

        return response;
    }

    private AiChatResponse buildDefaultResponse() {
        AiChatResponse response = new AiChatResponse();
        response.setIntent(AiIntentType.UNKNOWN.name());
        response.setReply("你可以问我附近有什么店、推荐吃什么、某家店有什么优惠券，或者咨询团购和退款规则。");
        response.setSuggestions(buildDefaultSuggestions());
        return response;
    }

    private List<String> buildDefaultSuggestions() {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("推荐附近火锅");
        suggestions.add("推荐附近KTV");
        suggestions.add("海底捞有什么优惠券");
        suggestions.add("团购券过期能退吗");
        return suggestions;
    }

    /**
     * 简单意图识别
     * 按当前数据库场景做了适配
     */
    private AiIntentType detectIntent(AiChatRequest request) {
        if (request == null || StrUtil.isBlank(request.getMessage())) {
            return AiIntentType.UNKNOWN;
        }

        String msg = request.getMessage().trim();

        // 如果前端明确带了 shopId，优先识别"查看详情"
        if (request.getShopId() != null && containsAny(msg, "查看详情", "店铺详情", "这家店详情", "看看详情", "详细信息")) {
            return AiIntentType.SHOP_DETAIL;
        }

        // 规则问答
        if (containsAny(msg, "退款", "过期", "规则", "秒杀规则", "团购规则", "叠加")) {
            return AiIntentType.RULE_QA;
        }

        // 查看详情：放在搜索前面
        if (containsAny(msg, "查看详情", "店铺详情", "这家店详情", "看看详情", "详细信息")) {
            return AiIntentType.SHOP_DETAIL;
        }

        // 优惠券规则问答
        if (containsAny(msg, "这张券怎么用", "券规则", "代金券怎么用", "优惠券怎么用", "这个券怎么用", "这个代金券规则")) {
            return AiIntentType.VOUCHER_RULE_QA;
        }

        // 店铺基础说明问答
        if (containsAny(msg, "营业到几点", "营业时间", "在哪", "地址", "人均多少", "哪个商圈", "在哪里")) {
            return AiIntentType.SHOP_BASE_QA;
        }

        if (containsAny(msg, "适合约会", "环境怎么样", "值不值得去", "用户评价", "评价怎么样", "有什么特点")) {
            return AiIntentType.SHOP_PROFILE_QA;
        }

        // 查券
        if (containsAny(msg, "优惠券", "团购券", "秒杀券", "有什么券", "有啥券", "查询优惠券", "查优惠券")) {
            return AiIntentType.COUPON_QUERY;
        }

        // 推荐
        if (containsAny(msg, "推荐", "适合", "附近有什么", "人均", "评分高", "离我近", "附近")) {
            return AiIntentType.RECOMMEND;
        }

        // 搜店
        if (containsAny(msg, "海底捞", "茶餐厅", "火锅店", "KTV", "搜一下", "查一下", "店", "商家")) {
            return AiIntentType.SHOP_SEARCH;
        }

        return AiIntentType.UNKNOWN;
    }

    /**
     * 合并本轮请求信息到上下文
     */
    private void mergeRequestToContext(AiChatRequest request, AiSessionContextDTO context) {
        if (request.getUserId() != null) {
            context.setUserId(request.getUserId());
        }
        if (request.getLat() != null) {
            context.setLat(request.getLat());
        }
        if (request.getLon() != null) {
            context.setLon(request.getLon());
        }

        // 如果前端显式传了 shopId，也更新上下文
        if (request.getShopId() != null) {
            ShopSimpleDTO shop = shopTool.getShopById(request.getShopId());
            if (shop != null) {
                context.setLastShopId(shop.getShopId());
                context.setLastShopName(shop.getName());
            }
        }

        String msg = request.getMessage();
        if (StrUtil.isBlank(msg)) {
            return;
        }

        // 品类/细分类
        if (containsAny(msg, "火锅")) {
            context.setCategoryKeyword("火锅");
        } else if (containsAny(msg, "日料", "寿司", "刺身")) {
            context.setCategoryKeyword("日料");
        } else if (containsAny(msg, "烧烤", "烤肉")) {
            context.setCategoryKeyword("烧烤");
        } else if (containsAny(msg, "茶餐厅", "冰厅")) {
            context.setCategoryKeyword("茶餐厅");
        } else if (containsAny(msg, "KTV", "唱歌")) {
            context.setCategoryKeyword("KTV");
        } else if (containsAny(msg, "美食", "餐厅")) {
            context.setCategoryKeyword("美食");
        }

        // 场景
        if (msg.contains("约会")) {
            context.setScene("约会");
        } else if (msg.contains("聚餐")) {
            context.setScene("聚餐");
        } else if (msg.contains("亲子")) {
            context.setScene("亲子");
        }

        if (msg.contains("环境好") || msg.contains("氛围好") || msg.contains("适合拍照")) {
            context.setPreferGoodEnvironment(true);
        }

        if (msg.contains("性价比高") || msg.contains("划算") || msg.contains("便宜点")) {
            context.setPreferHighValue(true);
        }

        // 预算
        Integer budget = extractBudget(msg);
        if (budget != null) {
            context.setBudget(budget);
        }

        // 评分
        Double minScore = extractMinScore(msg);
        if (minScore != null) {
            context.setMinScore(minScore);
        }

        // 当前消息提到新店名，则优先覆盖旧上下文
        String possibleShopName = handlerUtils.extractPossibleShopName(msg);
        if (StrUtil.isNotBlank(possibleShopName)) {
            context.setLastShopName(possibleShopName);
        }
    }

    /**
     * 支持 "100以内" / "150元以内"
     */
    private Integer extractBudget(String msg) {
        Pattern pattern = Pattern.compile("(\\d+)\\s*(元)?\\s*以内");
        Matcher matcher = pattern.matcher(msg);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    private Double extractMinScore(String msg) {
        if (StrUtil.isBlank(msg)) {
            return null;
        }

        // 支持：4.5分以上 / 4分以上 / 4.8以上
        Pattern pattern = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*分?以上");
        Matcher matcher = pattern.matcher(msg);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }

        // 支持模糊表达：评分高一点 / 高分 / 评分高
        if (msg.contains("评分高一点") || msg.contains("高分") || msg.contains("评分高")) {
            return 4.5;
        }

        return null;
    }

    private boolean containsAny(String msg, String... keywords) {
        if (StrUtil.isBlank(msg) || keywords == null || keywords.length == 0) {
            return false;
        }
        for (String keyword : keywords) {
            if (msg.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private AiSessionContextDTO getContext(String sessionId) {
        try {
            String key = AiRedisKeyConstants.AI_SESSION_KEY + sessionId;
            String json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isBlank(json)) {
                return null;
            }
            return MAPPER.readValue(json, AiSessionContextDTO.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void saveContext(String sessionId, AiSessionContextDTO context) {
        try {
            String key = AiRedisKeyConstants.AI_SESSION_KEY + sessionId;
            String json = MAPPER.writeValueAsString(context);
            stringRedisTemplate.opsForValue().set(key, json, 30, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("保存AI会话上下文失败 sessionId={}", sessionId, e);
        }
    }
}
