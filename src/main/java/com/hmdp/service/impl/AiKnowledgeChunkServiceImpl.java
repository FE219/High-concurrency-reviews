package com.hmdp.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.AiKnowledgeChunk;
import com.hmdp.mapper.AiKnowledgeChunkMapper;
import com.hmdp.service.IAiKnowledgeChunkService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiKnowledgeChunkServiceImpl extends ServiceImpl<AiKnowledgeChunkMapper, AiKnowledgeChunk>
        implements IAiKnowledgeChunkService {

    @Override
    public List<AiKnowledgeChunk> queryByBizType(String bizType) {
        return lambdaQuery()
                .eq(AiKnowledgeChunk::getBizType, bizType)
                .eq(AiKnowledgeChunk::getStatus, 1)
                .list();
    }

    @Override
    public List<AiKnowledgeChunk> queryByBizTypeAndBizId(String bizType, Long bizId) {
        return lambdaQuery()
                .eq(AiKnowledgeChunk::getBizType, bizType)
                .eq(AiKnowledgeChunk::getBizId, bizId)
                .eq(AiKnowledgeChunk::getStatus, 1)
                .list();
    }
}