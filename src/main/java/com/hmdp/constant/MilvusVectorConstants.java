package com.hmdp.constant;

public class MilvusVectorConstants {

    private MilvusVectorConstants() {
    }

    /**
     * FAQ 向量集合名
     */
    public static final String FAQ_COLLECTION = "faq_vector";

    /**
     * Blog 向量集合名
     */
    public static final String BLOG_COLLECTION = "blog_vector";

    /**
     * 向量维度
     * 必须和实际 embedding 输出一致
     * 你当前测试结果是 1024
     */
    public static final int VECTOR_DIM = 1024;

    /**
     * 通用字段名
     */
    public static final String FIELD_ID = "id";
    public static final String FIELD_DOC_ID = "docId";
    public static final String FIELD_SHOP_ID = "shopId";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_CONTENT = "content";
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_VECTOR = "vector";

    /**
     * 默认召回数量
     */
    public static final int FAQ_TOP_K = 3;
    public static final int BLOG_TOP_K = 3;
}