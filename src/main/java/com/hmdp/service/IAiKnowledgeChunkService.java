package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.AiKnowledgeChunk;

import java.util.List;

public interface IAiKnowledgeChunkService extends IService<AiKnowledgeChunk> {

    List<AiKnowledgeChunk> queryByBizType(String bizType);
    List<AiKnowledgeChunk> queryByBizTypeAndBizId(String bizType, Long bizId);

}