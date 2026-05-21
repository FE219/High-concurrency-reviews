package com.hmdp.dto.memory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_ai_rule_doc")
public class AiRuleDoc {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 规则标题
     */
    private String title;

    /**
     * 命中关键词，多个逗号分隔
     */
    private String keywords;

    /**
     * 规则内容
     */
    private String content;

    /**
     * 分类
     */
    private String category;

    /**
     * 状态：1启用 0停用
     */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}