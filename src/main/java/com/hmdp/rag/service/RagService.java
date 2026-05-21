package com.hmdp.rag.service;


import com.hmdp.dto.memory.AiSessionContextDTO;
import com.hmdp.rag.dto.RagContextDTO;

public interface RagService {

    /**
     * 检索知识上下文
     */
    RagContextDTO retrieveContext(String question, AiSessionContextDTO context);
}