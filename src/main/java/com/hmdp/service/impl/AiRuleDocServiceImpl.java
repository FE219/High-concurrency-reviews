package com.hmdp.service.impl;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.memory.AiRuleDoc;
import com.hmdp.mapper.AiRuleDocMapper;
import com.hmdp.service.IAiRuleDocService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class AiRuleDocServiceImpl extends ServiceImpl<AiRuleDocMapper, AiRuleDoc>
        implements IAiRuleDocService {

    @Override
    public List<AiRuleDoc> searchRules(String query) {
        if (StrUtil.isBlank(query)) {
            return Collections.emptyList();
        }

        // 1. 先提取关键词
        List<String> keywords = extractRuleKeywords(query);
        if (keywords.isEmpty()) {
            // 如果没提取出来，就退化成整句匹配
            return query()
                    .eq("status", 1)
                    .and(wrapper -> wrapper
                            .like("title", query)
                            .or()
                            .like("keywords", query)
                            .or()
                            .like("content", query)
                    )
                    .list();
        }

        // 2. 关键词命中查询
        return query()
                .eq("status", 1)
                .and(wrapper -> {
                    boolean first = true;
                    for (String keyword : keywords) {
                        if (first) {
                            wrapper.like("title", keyword)
                                    .or()
                                    .like("keywords", keyword)
                                    .or()
                                    .like("content", keyword);
                            first = false;
                        } else {
                            wrapper.or().like("title", keyword)
                                    .or()
                                    .like("keywords", keyword)
                                    .or()
                                    .like("content", keyword);
                        }
                    }
                })
                .list();
    }

    private List<String> extractRuleKeywords(String query) {
        List<String> list = new ArrayList<>();

        if (query.contains("退款") || query.contains("退")) {
            list.add("退款");
        }
        if (query.contains("过期")) {
            list.add("过期");
        }
        if (query.contains("团购券")) {
            list.add("团购券");
        }
        if (query.contains("优惠券")) {
            list.add("优惠券");
        }
        if (query.contains("叠加")) {
            list.add("叠加");
        }
        if (query.contains("秒杀")) {
            list.add("秒杀");
        }
        if (query.contains("规则")) {
            list.add("规则");
        }
        if (query.contains("使用")) {
            list.add("使用");
        }

        return list;
    }
}