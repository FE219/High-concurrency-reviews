package com.hmdp.service;


import com.hmdp.dto.request.AiChatRequest;
import com.hmdp.dto.response.AiChatResponse;

public interface AiChatService {
    AiChatResponse chat(AiChatRequest request);
}