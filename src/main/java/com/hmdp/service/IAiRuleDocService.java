package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.memory.AiRuleDoc;

import java.util.List;

public interface IAiRuleDocService extends IService<AiRuleDoc> {

    /**
     * 根据用户问题搜索规则
     */
    List<AiRuleDoc> searchRules(String query);
}