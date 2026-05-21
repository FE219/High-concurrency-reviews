package com.hmdp.service.impl;

import com.hmdp.dto.request.AiChatRequest;
import com.hmdp.dto.response.AiChatResponse;
import com.hmdp.dto.tool.RuleDocDTO;
import com.hmdp.enums.AiIntentType;
import com.hmdp.service.AiRuleQaService;
import com.hmdp.tool.RuleTool;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class AiRuleQaServiceImpl implements AiRuleQaService {

    @Resource
    private RuleTool ruleTool;

    @Override
    public AiChatResponse answerRule(AiChatRequest request) {
        AiChatResponse response = new AiChatResponse();
        response.setIntent(AiIntentType.RULE_QA.name());

        List<RuleDocDTO> docs = ruleTool.searchRules(request.getMessage());
        if (docs == null || docs.isEmpty()) {
            response.setReply("暂时没有查询到明确规则，你可以换一种问法，或者去帮助中心查看。");
            return response;
        }

        RuleDocDTO doc = docs.get(0);
        response.setReply(doc.getContent());
        return response;
    }
}