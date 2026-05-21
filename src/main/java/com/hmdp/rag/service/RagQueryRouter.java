package com.hmdp.rag.service;

import com.hmdp.rag.enums.RagSourceType;

public interface RagQueryRouter {

    /**
     * 判断问题应该走哪类知识源
     */
    RagSourceType route(String question);
}