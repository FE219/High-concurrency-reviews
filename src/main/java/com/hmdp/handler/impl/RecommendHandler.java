package com.hmdp.handler.impl;

import com.hmdp.dto.request.AiChatRequest;
import com.hmdp.dto.memory.AiSessionContextDTO;
import com.hmdp.dto.response.AiChatResponse;
import com.hmdp.handler.AiChatHandler;
import com.hmdp.service.AiRecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecommendHandler implements AiChatHandler {

    private final AiRecommendService aiRecommendService;

    @Override
    public AiChatResponse handle(AiChatRequest request, AiSessionContextDTO context) {
        return aiRecommendService.recommend(request, context);
    }
}
