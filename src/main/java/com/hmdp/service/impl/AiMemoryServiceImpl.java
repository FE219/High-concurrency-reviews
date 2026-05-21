package com.hmdp.service.impl;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.constant.AiRedisKeyConstants;
import com.hmdp.dto.memory.AiSessionContextDTO;
import com.hmdp.service.AiMemoryService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;


import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Service
public class AiMemoryServiceImpl implements AiMemoryService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public AiSessionContextDTO getContext(String sessionId) {
        try {
            String key = AiRedisKeyConstants.AI_SESSION_KEY + sessionId;
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return MAPPER.readValue(json, AiSessionContextDTO.class);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void saveContext(String sessionId, AiSessionContextDTO context) {
        try {
            String key = AiRedisKeyConstants.AI_SESSION_KEY + sessionId;
            String json = MAPPER.writeValueAsString(context);
            stringRedisTemplate.opsForValue().set(key, json, 30, TimeUnit.MINUTES);
        } catch (Exception e) {
            // 这里先简单打印，后续可接日志
            e.printStackTrace();
        }
    }
}