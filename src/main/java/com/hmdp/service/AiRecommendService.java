package com.hmdp.service;


import com.hmdp.dto.memory.AiSessionContextDTO;
import com.hmdp.dto.request.AiChatRequest;
import com.hmdp.dto.response.AiChatResponse;

public interface AiRecommendService {
    AiChatResponse recommend(AiChatRequest request, AiSessionContextDTO context);
}