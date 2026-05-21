package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.response.ShopProfileTagDTO;
import com.hmdp.service.AiLlmService;
import com.hmdp.service.ShopProfileService;
import com.hmdp.tool.ShopProfileTool;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Service
public class ShopProfileServiceImpl implements ShopProfileService {

    private static final String SHOP_PROFILE_KEY_PREFIX = "ai:shop:profile:tag:";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopProfileTool shopProfileTool;

    @Resource
    private AiLlmService aiLlmService;

    @Override
    public ShopProfileTagDTO getShopProfileTag(Long shopId, String shopName) {
        if (shopId == null) {
            return null;
        }

        String key = SHOP_PROFILE_KEY_PREFIX + shopId;
        String json = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(json)) {
            try {
                return MAPPER.readValue(json, ShopProfileTagDTO.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return rebuildShopProfileTag(shopId, shopName);
    }

    @Override
    public ShopProfileTagDTO rebuildShopProfileTag(Long shopId, String shopName) {
        if (shopId == null) {
            return null;
        }

        String profileContext = shopProfileTool.buildShopProfileContext(shopId, shopName);
        if (StrUtil.isBlank(profileContext)) {
            return null;
        }

        try {
            // 这里先用一个简单标签抽取逻辑，后面可换成更严格 JSON 输出
            String summary = aiLlmService.generateShopProfileReply("请总结这家店的特点、适合场景、环境风格和消费体验", profileContext);

            ShopProfileTagDTO dto = new ShopProfileTagDTO();
            dto.setShopId(shopId);
            dto.setSummary(summary);

            // 先用轻量规则从 summary 中提标签
            dto.setSceneTags(extractSceneTags(summary));
            dto.setStyleTags(extractStyleTags(summary));
            dto.setFeatureTags(extractFeatureTags(summary));

            String key = SHOP_PROFILE_KEY_PREFIX + shopId;
            stringRedisTemplate.opsForValue().set(key, MAPPER.writeValueAsString(dto), 1, TimeUnit.DAYS);

            return dto;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private java.util.List<String> extractSceneTags(String text) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (text == null) return list;

        if (text.contains("约会")) list.add("约会");
        if (text.contains("聚餐")) list.add("聚餐");
        if (text.contains("亲子")) list.add("亲子");
        if (text.contains("拍照")) list.add("拍照");
        return list;
    }

    private java.util.List<String> extractStyleTags(String text) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (text == null) return list;

        if (text.contains("浪漫")) list.add("浪漫");
        if (text.contains("环境")) list.add("环境好");
        if (text.contains("精致")) list.add("精致");
        if (text.contains("氛围")) list.add("氛围好");
        return list;
    }

    private java.util.List<String> extractFeatureTags(String text) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (text == null) return list;

        if (text.contains("性价比")) list.add("性价比高");
        if (text.contains("服务")) list.add("服务好");
        if (text.contains("颜值")) list.add("菜品颜值高");
        if (text.contains("划算")) list.add("划算");
        return list;
    }
}