package com.hmdp.service.impl;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.constant.AiRedisKeyConstants;
import com.hmdp.dto.AiEvidenceDTO;
import com.hmdp.dto.memory.AiSessionContextDTO;
import com.hmdp.dto.request.AiChatRequest;
import com.hmdp.dto.response.AiChatResponse;
import com.hmdp.dto.response.RecommendationItemDTO;
import com.hmdp.dto.tool.ShopSimpleDTO;
import com.hmdp.dto.tool.VoucherSimpleDTO;
import com.hmdp.enums.AiIntentType;
import com.hmdp.rag.dto.RagContextDTO;
import com.hmdp.rag.service.RagService;
import com.hmdp.service.AiChatService;
import com.hmdp.service.AiLlmService;
import com.hmdp.service.AiRecommendService;
import com.hmdp.service.UserProfileService;
import com.hmdp.tool.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiChatServiceImpl implements AiChatService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private ShopProfileTool shopProfileTool;

    @Resource
    private RuleTool ruleTool;

    @Resource
    private AiLlmService aiLlmService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AiRecommendService aiRecommendService;

    @Resource
    private ShopTool shopTool;

    @Resource
    private CouponTool couponTool;

    @Resource
    private UserProfileService userProfileService;

    @Resource
    private RagService ragService;

    @Resource
    private BlogTool blogTool;

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
        switch (intent) {
            case RECOMMEND:
                response = aiRecommendService.recommend(request, context);
                break;
            case COUPON_QUERY:
                response = handleCouponQuery(request, context);
                break;
            case SHOP_SEARCH:
                response = handleShopSearch(request, context);
                break;
            case RULE_QA:
                response = handleRuleQa(request.getMessage(), context);
                break;
            case SHOP_DETAIL:
                response = handleShopDetail(request, context);
                break;
            case SHOP_PROFILE_QA:
                response = handleShopProfileQa(request.getMessage(), context);
                break;
            case VOUCHER_RULE_QA:
                response = handleVoucherRuleQa(request, context);
                break;
            case SHOP_BASE_QA:
                response = handleShopBaseQa(request, context);
                break;
            default:
                response = buildDefaultResponse();
                break;
        }

        // 7. 保存上下文
        saveContext(request.getSessionId(), context);

        return response;
    }

    private AiChatResponse handleShopProfileQa(String message, AiSessionContextDTO context) {
        Long shopId = context != null ? context.getLastShopId() : null;
        String shopName = context != null ? context.getLastShopName() : null;

        if (shopId == null) {
            return buildTextResponse(
                    "你想了解哪家店呢？可以先告诉我店名，或者先点击推荐卡片里的“查看详情”。",
                    "SHOP_PROFILE_QA",
                    context,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    true
            );
        }

        List<AiEvidenceDTO> evidenceList = new ArrayList<>();

        // 1. 商户画像摘要
        AiEvidenceDTO profileEvidence = shopProfileTool.queryShopProfileEvidence(shopId, shopName);
        if (profileEvidence != null) {
            evidenceList.add(profileEvidence);
        }

        // 2. blog evidence：Milvus 向量检索优先 + DB fallback
        List<AiEvidenceDTO> blogEvidenceList = blogTool.queryShopBlogEvidence(shopId, message, 3);
        if (blogEvidenceList != null && !blogEvidenceList.isEmpty()) {
            evidenceList.addAll(blogEvidenceList);
        }

        List<String> knowledgeSources = evidenceList.stream()
                .map(AiEvidenceDTO::getSourceType)
                .filter(this::isNotBlank)
                .distinct()
                .collect(Collectors.toList());

        List<String> hitTitles = evidenceList.stream()
                .map(AiEvidenceDTO::getTitle)
                .filter(this::isNotBlank)
                .collect(Collectors.toList());

        log.info("handleShopProfileQa question={}, shopId={}, shopName={}, evidenceCount={}, sources={}, titles={}",
                message, shopId, shopName,
                evidenceList.size(), knowledgeSources, hitTitles);

        if (evidenceList.isEmpty()) {
            String displayName = isBlank(shopName) ? "这家店" : shopName;
            return buildTextResponse(
                    displayName + " 暂时还没有足够的探店内容，我目前没法给你较准确的环境或体验判断。你也可以先看看店铺评分、详情和优惠券信息。",
                    "SHOP_PROFILE_QA",
                    context,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    true
            );
        }

        String fallbackAnswer = buildShopProfileFallbackAnswer(shopName, evidenceList);
        String answer = aiLlmService.answerWithEvidence(message, evidenceList, fallbackAnswer);

        boolean fallbackUsed = isBlank(answer);
        if (fallbackUsed) {
            answer = fallbackAnswer;
        }

        return buildTextResponse(
                answer,
                "SHOP_PROFILE_QA",
                context,
                knowledgeSources,
                hitTitles,
                fallbackUsed
        );
    }

    private String buildShopProfileFallbackAnswer(String shopName, List<AiEvidenceDTO> evidenceList) {
        String displayName = isBlank(shopName) ? "这家店" : shopName;

        String profileSummary = null;
        for (AiEvidenceDTO evidence : evidenceList) {
            if ("SHOP_PROFILE".equals(evidence.getSourceType())
                    && isNotBlank(evidence.getContent())) {
                profileSummary = evidence.getContent();
                break;
            }
        }

        if (isNotBlank(profileSummary)) {
            return "结合现有探店内容看，" + displayName + "大致情况是：" + profileSummary;
        }

        return displayName + " 目前有一些用户探店反馈，但信息还不算特别完整。建议你结合店铺评分、优惠券和详情信息一起判断。";
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


    private String generateRagReply(String question, AiSessionContextDTO context, String defaultReply) {
        try {
            RagContextDTO ragContext = ragService.retrieveContext(question, context);
            if (ragContext == null || StrUtil.isBlank(ragContext.getContextText())) {
                log.info("[RAG] 未检索到上下文，使用默认回复");
                return defaultReply;
            }

            String aiReply = aiLlmService.generateRagAnswer(question, ragContext.getContextText());
            if (StrUtil.isNotBlank(aiReply)) {
                log.info("[RAG] 模型回答={}", aiReply);
                return aiReply;
            }
        } catch (Exception e) {
            log.error("[RAG] 模型生成失败 question={}", question, e);
        }

        log.warn("[RAG] 模型生成失败，回退默认回复");
        return defaultReply;
    }

    private AiChatResponse handleVoucherRuleQa(AiChatRequest request, AiSessionContextDTO context) {
        AiChatResponse response = new AiChatResponse();
        response.setIntent(AiIntentType.VOUCHER_RULE_QA.name());

        Long shopId = resolveShopId(request, context);
        if (shopId == null) {
            response.setReply("请先告诉我你想咨询哪家店的优惠券规则，或者先从推荐卡片中选择一家店。");
            response.setSuggestions(buildVoucherRuleSuggestions());
            return response;
        }

        String defaultReply = "当前没有查到这家店优惠券的详细规则说明，你可以先查看这家店的优惠券列表。";
        String reply = generateRagReply(request.getMessage(), context, defaultReply);

        response.setReply(reply);
        response.setSuggestions(buildVoucherRuleSuggestions());
        return response;
    }

    private AiChatResponse handleShopBaseQa(AiChatRequest request, AiSessionContextDTO context) {
        AiChatResponse response = new AiChatResponse();
        response.setIntent(AiIntentType.SHOP_BASE_QA.name());

        Long shopId = resolveShopId(request, context);
        if (shopId == null) {
            response.setReply("请先告诉我你想了解哪家店，或者先从推荐卡片中选择一家店。");
            response.setSuggestions(buildShopBaseSuggestions());
            return response;
        }

        String defaultReply = "当前没有查到这家店更完整的基础信息，你可以尝试查看这家店的详情。";
        String reply = generateRagReply(request.getMessage(), context, defaultReply);

        response.setReply(reply);
        response.setSuggestions(buildShopBaseSuggestions());
        return response;
    }

    private List<String> buildVoucherRuleSuggestions() {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("这张券怎么用");
        suggestions.add("这张券有什么限制");
        suggestions.add("这家店有什么优惠券");
        return suggestions;
    }

    private List<String> buildShopBaseSuggestions() {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("这家店营业到几点");
        suggestions.add("这家店人均多少");
        suggestions.add("这家店在哪");
        suggestions.add("查看这家店详情");
        return suggestions;
    }
    private AiChatResponse handleRagQa(AiChatRequest request, AiSessionContextDTO context) {
        AiChatResponse response = new AiChatResponse();
        response.setIntent("RAG_QA");

        RagContextDTO ragContext = ragService.retrieveContext(request.getMessage(), context);
        if (ragContext == null || StrUtil.isBlank(ragContext.getContextText())) {
            response.setReply("当前没有足够信息回答这个问题，你可以换种问法，或者先选择一家店再问我。");
            return response;
        }

        String defaultReply = "我找到了一些相关信息，但当前无法更准确地组织回答。";

        try {
            String aiReply = aiLlmService.generateRagAnswer(request.getMessage(), ragContext.getContextText());
            if (StrUtil.isNotBlank(aiReply)) {
                response.setReply(aiReply);
            } else {
                response.setReply(defaultReply);
            }
        } catch (Exception e) {
            log.error("RAG知识库问答失败 shopId={}", context.getLastShopId(), e);
            response.setReply(defaultReply);
        }

        response.setSuggestions(buildRagSuggestions());
        return response;
    }

    private List<String> buildRagSuggestions() {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("这家店适合约会吗");
        suggestions.add("这家店环境怎么样");
        suggestions.add("团购券过期能退吗");
        suggestions.add("这张券怎么用");
        return suggestions;
    }
    /**
     * 优惠券查询
     * 支持：
     * 1. “这家店有什么券” -> 使用上下文中的 lastShopId
     * 2. “海底捞有什么券” -> 先按关键词搜店
     */

    private AiChatResponse handleShopDetail(AiChatRequest request, AiSessionContextDTO context) {
        AiChatResponse response = new AiChatResponse();
        response.setIntent(AiIntentType.SHOP_DETAIL.name());

        Long shopId = resolveShopId(request, context);
        if (shopId == null) {
            response.setReply("请先告诉我你想查看哪家店的详情，或者直接点击推荐卡片上的“查看详情”按钮。");
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
            sb.append("评分").append(formatMoney(shop.getScore())).append("分；");
        }
        if (shop.getAvgPrice() != null) {
            sb.append("人均").append(formatMoney(shop.getAvgPrice())).append("元；");
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


    private AiChatResponse handleCouponQuery(AiChatRequest request, AiSessionContextDTO context) {
        AiChatResponse response = new AiChatResponse();
        response.setIntent(AiIntentType.COUPON_QUERY.name());

        Long shopId = resolveShopId(request, context);

        if (shopId == null) {
            response.setReply("请先告诉我你想查询哪家店的优惠券，比如“海底捞有什么券？”，或者直接点击推荐卡片上的“查优惠券”按钮。");
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
                        .append(formatMoney(coupon.getPayValue()))
                        .append("元，可抵")
                        .append(formatMoney(coupon.getActualValue()))
                        .append("元");
            } else if (coupon.getPayValue() != null) {
                sb.append("到手价").append(formatMoney(coupon.getPayValue())).append("元");
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

    /**
     * 店铺搜索
     * 支持：
     * - “搜一下海底捞”
     * - “查一下KTV”
     * - “附近有什么店”
     */
    private AiChatResponse handleShopSearch(AiChatRequest request, AiSessionContextDTO context) {
        AiChatResponse response = new AiChatResponse();
        response.setIntent(AiIntentType.SHOP_SEARCH.name());

        String keyword = extractSearchKeyword(request.getMessage(), context);
        if (StrUtil.isBlank(keyword)) {
            response.setReply("你可以告诉我想找什么店，比如“海底捞”“火锅店”“附近KTV”。");
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
                    dto.setReason(buildSearchReason(shop));
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

    private AiChatResponse handleRuleQa(String message, AiSessionContextDTO context) {
        // 1. 检索规则 evidence（Milvus 向量检索优先 + 关键词 fallback）
        List<AiEvidenceDTO> evidenceList = ruleTool.queryRuleEvidence(message);

        // 2. 提取命中标题
        List<String> hitTitles = evidenceList == null ? Collections.emptyList()
                : evidenceList.stream()
                .map(AiEvidenceDTO::getTitle)
                .filter(this::isNotBlank)
                .collect(Collectors.toList());

        log.info("handleRuleQa question={}, evidenceCount={}, hitTitles={}",
                message,
                evidenceList == null ? 0 : evidenceList.size(),
                hitTitles);

        // 3. 无命中直接兜底
        if (evidenceList == null || evidenceList.isEmpty()) {
            return buildTextResponse(
                    "暂时没有查到与你问题相关的平台规则，你可以换个更具体的问法，比如“优惠券过期了怎么办”或“秒杀券可以提现吗”。",
                    "RULE_QA",
                    context,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    true
            );
        }

        // 4. 构造 fallback 答案
        String fallbackAnswer = ruleTool.buildRuleFallbackAnswer(evidenceList);

        // 5. 基于 evidence 调大模型生成
        String answer = aiLlmService.answerWithEvidence(message, evidenceList, fallbackAnswer);

        boolean fallbackUsed = isBlank(answer);
        if (fallbackUsed) {
            answer = fallbackAnswer;
        }

        log.info("handleRuleQa final answer={}, fallbackUsed={}", answer, fallbackUsed);
        // 6. 返回响应
        return buildTextResponse(
                answer,
                "RULE_QA",
                context,
                Collections.singletonList("RULE_DOC"),
                hitTitles,
                fallbackUsed
        );
    }


    private AiChatResponse buildDefaultResponse() {
        AiChatResponse response = new AiChatResponse();
        response.setIntent(AiIntentType.UNKNOWN.name());
        response.setReply("你可以问我附近有什么店、推荐吃什么、某家店有什么优惠券，或者咨询团购和退款规则。");
        response.setSuggestions(buildDefaultSuggestions());
        return response;
    }

    private AiChatResponse buildTextResponse(String reply,
                                             String intent,
                                             AiSessionContextDTO context,
                                             List<String> knowledgeSources,
                                             List<String> hitTitles,
                                             boolean fallback) {
        AiChatResponse response = new AiChatResponse();
        response.setReply(reply);
        response.setIntent(intent);
        response.setSessionId(context != null ? context.getSessionId() : null);
        response.setKnowledgeSources(knowledgeSources);
        response.setHitTitles(hitTitles);
        response.setFallback(fallback);
        return response;
    }

    private boolean isNotBlank(String str) {
        return str != null && !str.trim().isEmpty();
    }
    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
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

        // 如果前端明确带了 shopId，优先识别“查看详情”
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
        String possibleShopName = extractPossibleShopName(msg);
        if (StrUtil.isNotBlank(possibleShopName)) {
            context.setLastShopName(possibleShopName);
        }
    }

    /**
     * 支持 “100以内” / “150元以内”
     */
    private Integer extractBudget(String msg) {
        Pattern pattern = Pattern.compile("(\\d+)\\s*(元)?\\s*以内");
        Matcher matcher = pattern.matcher(msg);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    /**
     * 解析店铺ID：
     * 1. 优先使用上下文中的 lastShopId
     * 2. 没有则尝试从消息里提取店名再搜索
     */
    private Long resolveShopId(AiChatRequest request, AiSessionContextDTO context) {
        // 1. 前端显式传了 shopId，优先使用
        if (request.getShopId() != null) {
            ShopSimpleDTO shop = shopTool.getShopById(request.getShopId());
            if (shop != null) {
                context.setLastShopId(shop.getShopId());
                context.setLastShopName(shop.getName());
                return shop.getShopId();
            }
        }

        // 2. 再从当前消息里提取店名
        String possibleShopName = extractPossibleShopName(request.getMessage());
        if (StrUtil.isNotBlank(possibleShopName)) {
            ShopSimpleDTO shop = shopTool.searchFirstByKeyword(possibleShopName);
            if (shop != null) {
                context.setLastShopId(shop.getShopId());
                context.setLastShopName(shop.getName());
                log.info("[resolveShopId] 使用当前消息店名={}, 解析shopId={}", possibleShopName, shop.getShopId());
                return shop.getShopId();
            }
        }

        // 3. 当前消息里没有明确店名，再回退到上下文
        if (context.getLastShopId() != null) {
            log.info("[resolveShopId] 使用历史上下文 shopId={}, shopName={}", context.getLastShopId(), context.getLastShopName());
            return context.getLastShopId();
        }

        // 4. 最后用 lastShopName 兜底
        if (StrUtil.isNotBlank(context.getLastShopName())) {
            ShopSimpleDTO shop = shopTool.searchFirstByKeyword(context.getLastShopName());
            if (shop != null) {
                context.setLastShopId(shop.getShopId());
                context.setLastShopName(shop.getName());
                log.info("[resolveShopId] 使用历史店名兜底 shopId={}", shop.getShopId());
                return shop.getShopId();
            }
        }
        log.info("[resolveShopId] 未解析到店铺");
        return null;
    }

    /**
     * 提取搜索关键词
     */
    private String extractSearchKeyword(String message, AiSessionContextDTO context) {
        String possibleShopName = extractPossibleShopName(message);
        if (StrUtil.isNotBlank(possibleShopName)) {
            return possibleShopName;
        }
        if (StrUtil.isNotBlank(context.getCategoryKeyword())) {
            return context.getCategoryKeyword();
        }
        return message;
    }

    /**
     * 简单识别常见品牌/店名关键词
     * 当前按你的样例数据做了适配
     */
    private String extractPossibleShopName(String message) {
        if (StrUtil.isBlank(message)) {
            return null;
        }

        if (message.contains("海底捞")) {
            return "海底捞";
        }
        if (message.contains("103茶餐厅")) {
            return "103茶餐厅";
        }
        if (message.contains("新白鹿")) {
            return "新白鹿";
        }
        if (message.contains("Mamala")) {
            return "Mamala";
        }
        if (message.contains("浅草屋")) {
            return "浅草屋";
        }
        if (message.contains("开乐迪")) {
            return "开乐迪";
        }
        if (message.contains("INLOVE")) {
            return "INLOVE";
        }
        if (message.contains("星聚会")) {
            return "星聚会";
        }

        return null;
    }

    private String buildSearchReason(ShopSimpleDTO shop) {
        List<String> reasons = new ArrayList<>();

        if (shop.getScore() != null && shop.getScore() >= 4.5) {
            reasons.add("评分较高");
        }
        if (shop.getAvgPrice() != null) {
            reasons.add("人均约" + formatMoney(shop.getAvgPrice()) + "元");
        }
        if (StrUtil.isNotBlank(shop.getArea())) {
            reasons.add("位于" + shop.getArea());
        }

        if (reasons.isEmpty()) {
            reasons.add("与关键词匹配");
        }
        return String.join("、", reasons);
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

    private List<String> buildDefaultSuggestions() {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("推荐附近火锅");
        suggestions.add("推荐附近KTV");
        suggestions.add("海底捞有什么优惠券");
        suggestions.add("团购券过期能退吗");
        return suggestions;
    }

    private List<String> buildSearchSuggestions() {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("查看这家店有什么优惠券");
        suggestions.add("推荐附近类似店铺");
        suggestions.add("只看评分高一点的");
        return suggestions;
    }

    private List<String> buildCouponSuggestions() {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("还有别的优惠吗");
        suggestions.add("推荐附近更划算的店");
        suggestions.add("再找一家类似的店");
        return suggestions;
    }

    private List<String> buildRuleSuggestions() {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("团购券过期能退吗");
        suggestions.add("优惠券可以叠加使用吗");
        suggestions.add("秒杀券有什么规则");
        return suggestions;
    }
}