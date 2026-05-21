package com.hmdp.dto;


public class AiEvidenceDTO {

    /**
     * 证据来源类型：RULE_DOC / BLOG / SHOP_PROFILE / SHOP / VOUCHER
     */
    private String sourceType;

    /**
     * 来源主键ID
     */
    private Long sourceId;

    /**
     * 证据标题
     */
    private String title;

    /**
     * 证据正文/摘要
     */
    private String content;

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}