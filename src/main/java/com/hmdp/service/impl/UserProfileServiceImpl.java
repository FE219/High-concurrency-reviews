package com.hmdp.service.impl;

import com.hmdp.dto.response.UserProfilePreferenceDTO;
import com.hmdp.service.UserProfileService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
public class UserProfileServiceImpl implements UserProfileService {

    private static final String PROFILE_KEY_PREFIX = "ai:user:profile:";
    private static final String CATEGORY_KEY_SUFFIX = ":category";
    private static final String AREA_KEY_SUFFIX = ":area";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public UserProfilePreferenceDTO getUserProfile(Long userId) {
        if (userId == null) {
            return null;
        }

        String profileKey = PROFILE_KEY_PREFIX + userId;
        String categoryKey = profileKey + CATEGORY_KEY_SUFFIX;
        String areaKey = profileKey + AREA_KEY_SUFFIX;

        Map<Object, Object> profileMap = stringRedisTemplate.opsForHash().entries(profileKey);

        UserProfilePreferenceDTO dto = new UserProfilePreferenceDTO();

        // 基础偏好
        if (profileMap != null && !profileMap.isEmpty()) {
            Object avgBudget = profileMap.get("avgBudget");
            if (avgBudget != null) {
                dto.setAvgBudget(Integer.parseInt(avgBudget.toString()));
            }

            Object preferHighScore = getObject(profileMap);
            if (preferHighScore != null) {
                dto.setPreferHighScore(Boolean.parseBoolean(preferHighScore.toString()));
            }

            Object preferCoupon = profileMap.get("preferCoupon");
            if (preferCoupon != null) {
                dto.setPreferCoupon(Boolean.parseBoolean(preferCoupon.toString()));
            }
        }

        // 品类偏好
        Set<String> categorySet = stringRedisTemplate.opsForZSet().reverseRange(categoryKey, 0, 9);
        Map<String, Integer> categoryPreference = new HashMap<>();
        if (categorySet != null) {
            for (String category : categorySet) {
                Double score = stringRedisTemplate.opsForZSet().score(categoryKey, category);
                if (score != null) {
                    categoryPreference.put(category, score.intValue());
                }
            }
        }
        dto.setCategoryPreference(categoryPreference);

        // 商圈偏好
        Set<String> areaSet = stringRedisTemplate.opsForZSet().reverseRange(areaKey, 0, 9);
        Map<String, Integer> areaPreference = new HashMap<>();
        if (areaSet != null) {
            for (String area : areaSet) {
                Double score = stringRedisTemplate.opsForZSet().score(areaKey, area);
                if (score != null) {
                    areaPreference.put(area, score.intValue());
                }
            }
        }
        dto.setAreaPreference(areaPreference);

        return dto;
    }

    private static Object getObject(Map<Object, Object> profileMap) {
        Object preferHighScore = profileMap.get("preferHighScore");
        return preferHighScore;
    }

    @Override
    public void recordCategoryPreference(Long userId, String category) {
        if (userId == null || category == null || category.trim().isEmpty()) {
            return;
        }
        String key = PROFILE_KEY_PREFIX + userId + CATEGORY_KEY_SUFFIX;
        stringRedisTemplate.opsForZSet().incrementScore(key, category, 1.0);
    }

    @Override
    public void recordAreaPreference(Long userId, String area) {
        if (userId == null || area == null || area.trim().isEmpty()) {
            return;
        }
        String key = PROFILE_KEY_PREFIX + userId + AREA_KEY_SUFFIX;
        stringRedisTemplate.opsForZSet().incrementScore(key, area, 1.0);
    }

    @Override
    public void recordBudgetPreference(Long userId, Integer budget) {
        if (userId == null || budget == null || budget <= 0) {
            return;
        }
        String profileKey = PROFILE_KEY_PREFIX + userId;

        Object oldBudgetObj = stringRedisTemplate.opsForHash().get(profileKey, "avgBudget");
        int newBudget = budget;

        if (oldBudgetObj != null) {
            int oldBudget = Integer.parseInt(oldBudgetObj.toString());
            // 简单移动平均
            newBudget = (oldBudget + budget) / 2;
        }

        stringRedisTemplate.opsForHash().put(profileKey, "avgBudget", String.valueOf(newBudget));
    }

    @Override
    public void recordPreferHighScore(Long userId) {
        if (userId == null) {
            return;
        }
        String profileKey = PROFILE_KEY_PREFIX + userId;
        stringRedisTemplate.opsForHash().put(profileKey, "preferHighScore", "true");
    }

    @Override
    public void recordPreferCoupon(Long userId) {
        if (userId == null) {
            return;
        }
        String profileKey = PROFILE_KEY_PREFIX + userId;
        stringRedisTemplate.opsForHash().put(profileKey, "preferCoupon", "true");
    }
}