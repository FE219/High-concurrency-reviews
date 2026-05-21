package com.hmdp.service;

import com.hmdp.dto.AiEvidenceDTO;
import com.hmdp.dto.response.RecommendationItemDTO;
import com.hmdp.dto.tool.ShopSimpleDTO;
import com.hmdp.dto.tool.VoucherSimpleDTO;


import java.util.List;

public interface AiLlmService {

    /**
     * 推荐回复润色
     */
    String polishRecommendReply(String userMessage, List<RecommendationItemDTO> recommendations);

    /**
     * 查券回复润色
     */
    String polishCouponReply(String shopName, List<VoucherSimpleDTO> coupons);

    /**
     * 店铺详情回复润色
     */
    String polishShopDetailReply(ShopSimpleDTO shop);


    String generateShopProfileReply(String userMessage, String profileContext);

    String generateRagAnswer(String question, String contextText);

    /**
     * 基础聊天/文本生成
     */
    String chat(String prompt);

    /**
     * 基于证据进行回答（轻量RAG）
     *
     * @param userQuestion 用户原始问题
     * @param evidenceList 检索出的证据列表
     * @param fallbackAnswer 模型失败时的兜底回答
     * @return 生成后的回答
     */
    String answerWithEvidence(String userQuestion, List<AiEvidenceDTO> evidenceList, String fallbackAnswer);}