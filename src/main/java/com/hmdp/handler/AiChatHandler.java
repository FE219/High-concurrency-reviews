package com.hmdp.handler;

import com.hmdp.dto.request.AiChatRequest;
import com.hmdp.dto.memory.AiSessionContextDTO;
import com.hmdp.dto.response.AiChatResponse;

public interface AiChatHandler {
    AiChatResponse handle(AiChatRequest request, AiSessionContextDTO context);
}
