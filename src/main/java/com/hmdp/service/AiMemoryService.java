package com.hmdp.service;


import com.hmdp.dto.memory.AiSessionContextDTO;

public interface AiMemoryService {
    AiSessionContextDTO getContext(String sessionId);
    void saveContext(String sessionId, AiSessionContextDTO context);
}