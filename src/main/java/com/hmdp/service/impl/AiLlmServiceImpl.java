package com.hmdp.service.impl;

import com.hmdp.constant.AiFallbackMessages;
import com.hmdp.dto.AiEvidenceDTO;
import com.hmdp.dto.response.RecommendationItemDTO;
import com.hmdp.dto.tool.ShopSimpleDTO;
import com.hmdp.dto.tool.VoucherSimpleDTO;
import com.hmdp.exception.AiServiceException;
import com.hmdp.prompt.AiPromptTemplates;
import com.hmdp.service.AiLlmService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import cn.hutool.core.util.StrUtil;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiLlmServiceImpl implements AiLlmService {

    @Resource
    private ChatLanguageModel chatLanguageModel;

    @Value("${ai.model.enabled:true}")
    private Boolean enabled;


    @Override
    public String generateShopProfileReply(String userMessage, String profileContext) {
        if (Boolean.FALSE.equals(enabled)) {
            return null;
        }
        if (profileContext == null || profileContext.trim().isEmpty()) {
            return null;
        }

        String prompt = String.format(AiPromptTemplates.SHOP_PROFILE_PROMPT, sanitize(userMessage), profileContext);
        return chatLanguageModel.generate(prompt);
    }


    @Override
    public String polishRecommendReply(String userMessage, List<RecommendationItemDTO> recommendations) {
        if (Boolean.FALSE.equals(enabled)) {
            return null;
        }
        if (recommendations == null || recommendations.isEmpty()) {
            return null;
        }

        String dataText = recommendations.stream()
                .map(item -> String.format(
                        "店名:%s, 评分:%s, 距离:%s, 人均:%s, 推荐理由:%s, 优惠信息:%s",
                        item.getShopName(),
                        item.getScore(),
                        item.getDistanceText(),
                        item.getAvgPriceText(),
                        item.getReason(),
                        item.getCouponSummary()
                ))
                .collect(Collectors.joining("\n"));

        String prompt = String.format(AiPromptTemplates.RECOMMEND_PROMPT, sanitize(userMessage), dataText);
        return chatLanguageModel.generate(prompt);
    }

    @Override
    public String polishCouponReply(String shopName, List<VoucherSimpleDTO> coupons) {
        if (Boolean.FALSE.equals(enabled)) {
            return null;
        }
        if (coupons == null || coupons.isEmpty()) {
            return null;
        }

        String dataText = coupons.stream()
                .map(coupon -> String.format(
                        "券名:%s, 到手价:%s元, 抵扣:%s元, 副标题:%s, 是否秒杀券:%s",
                        coupon.getTitle(),
                        coupon.getPayValue(),
                        coupon.getActualValue(),
                        coupon.getSubTitle(),
                        Boolean.TRUE.equals(coupon.getSeckill()) ? "是" : "否"
                ))
                .collect(Collectors.joining("\n"));

        String prompt = String.format(AiPromptTemplates.COUPON_PROMPT, shopName, dataText);
        return chatLanguageModel.generate(prompt);
    }

    @Override
    public String polishShopDetailReply(ShopSimpleDTO shop) {
        if (Boolean.FALSE.equals(enabled)) {
            return null;
        }
        if (shop == null) {
            return null;
        }

        String dataText = String.format(
                "店名:%s, 评分:%s, 人均:%s元, 商圈:%s, 地址:%s, 营业时间:%s",
                shop.getName(),
                shop.getScore(),
                shop.getAvgPrice(),
                shop.getArea(),
                shop.getAddress(),
                shop.getOpenHours()
        );

        String prompt = String.format(AiPromptTemplates.SHOP_DETAIL_PROMPT, dataText);
        return chatLanguageModel.generate(prompt);
    }

    @Override
    public String generateRagAnswer(String question, String contextText) {
        if (Boolean.FALSE.equals(enabled)) {
            return null;
        }
        if (contextText == null || contextText.trim().isEmpty()) {
            return null;
        }

        String prompt = String.format(AiPromptTemplates.RAG_QA_PROMPT, sanitize(question), contextText);
        return chatLanguageModel.generate(prompt);
    }


    private String sanitize(String input) {
        if (input == null) return "";
        return input
                .replace("\0", "")
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
    }

    private String buildEvidencePrompt(String userQuestion, String evidenceText) {
        return "你是黑马点评的智能导购助手。\n"
                + "请严格基于给定资料回答用户问题，不要编造资料中没有提到的信息。\n"
                + "如果资料无法明确回答，就明确说“目前资料中没有明确提到”，并给出谨慎建议。\n"
                + "如果资料之间信息不完全一致，请做保守总结，不要绝对化表述。\n"
                + "如果用户询问“适不适合、值不值得、好不好”等问题，请给出基于资料的倾向性建议，而不是绝对结论。\n"
                + "\n"
                + "回答要求：\n"
                + "1. 语言自然、简洁，像本地生活平台助手\n"
                + "2. 优先总结关键信息，不要逐字复述资料\n"
                + "3. 不要输出“根据资料1/资料2”这种生硬表达，尽量口语化\n"
                + "4. 如果问题是规则类问答，回答要明确、清晰\n"
                + "\n"
                + "用户问题：\n"
                + sanitize(userQuestion) + "\n"
                + "\n"
                + "参考资料：\n"
                + evidenceText;
    }

    @Override
    public String chat(String prompt) {
        try {
            // 1. 调用异步方法（带熔断+重试）
            String result = chatWithProtection(sanitize(prompt)).get(15, TimeUnit.SECONDS);
            return result;
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("AI call timed out");
            throw new AiServiceException("AI call timed out", false);
        } catch (Exception e) {
            log.error("AI call failed", e);
            throw new AiServiceException("AI call failed: " + e.getMessage(), e);
        }
    }

    @CircuitBreaker(name = "aiLlmCall", fallbackMethod = "chatFallback")
    @Retry(name = "aiLlmCall")
    public CompletableFuture<String> chatWithProtection(String prompt) {
        String result = chatLanguageModel.generate(prompt);
        return CompletableFuture.completedFuture(result);
    }

    private CompletableFuture<String> chatFallback(String prompt, AiServiceException e) {
        log.warn("AI fallback triggered, circuitOpen={}", e.isCircuitOpen());
        if (e.isCircuitOpen()) {
            return CompletableFuture.completedFuture(AiFallbackMessages.CIRCUIT_OPEN);
        }
        return CompletableFuture.completedFuture(AiFallbackMessages.GENERAL_ERROR);
    }

    private CompletableFuture<String> chatFallback(String prompt, Exception e) {
        log.warn("AI fallback triggered by unexpected exception: {}", e.getMessage());
        return CompletableFuture.completedFuture(AiFallbackMessages.GENERAL_ERROR);
    }

    private String buildEvidenceText(List<AiEvidenceDTO> evidenceList) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < evidenceList.size(); i++) {
            AiEvidenceDTO evidence = evidenceList.get(i);
            sb.append("资料").append(i + 1).append("：\n");
            sb.append("来源类型：").append(StrUtil.nullToEmpty(evidence.getSourceType())).append("\n");
            sb.append("标题：").append(StrUtil.nullToEmpty(evidence.getTitle())).append("\n");
            sb.append("内容：").append(StrUtil.nullToEmpty(evidence.getContent())).append("\n\n");
        }
        return sb.toString();
    }

    @Override
    public String answerWithEvidence(String userQuestion, List<AiEvidenceDTO> evidenceList, String fallbackAnswer) {
        try {
            if (evidenceList == null || evidenceList.isEmpty()) {
                return fallbackAnswer;
            }
            String evidenceText = buildEvidenceText(evidenceList);
            String prompt = buildEvidencePrompt(userQuestion, evidenceText);
            // Use protected chat call
            String result = chat(prompt);
            if (StrUtil.isBlank(result)) {
                return fallbackAnswer;
            }
            return result.trim();
        } catch (AiServiceException e) {
            log.warn("answerWithEvidence AI failure, using fallback");
            return fallbackAnswer;
        } catch (Exception e) {
            log.error("answerWithEvidence error, question={}", userQuestion, e);
            return fallbackAnswer;
        }
    }
}