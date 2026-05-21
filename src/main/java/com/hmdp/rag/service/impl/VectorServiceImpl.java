package com.hmdp.rag.service.impl;

import com.hmdp.constant.MilvusVectorConstants;
import com.hmdp.dto.AiEvidenceDTO;

import com.hmdp.dto.memory.AiRuleDoc;
import com.hmdp.entity.Blog;
import com.hmdp.rag.service.EmbeddingService;
import com.hmdp.rag.service.VectorService;
import com.hmdp.service.IAiRuleDocService;
import com.hmdp.service.IBlogService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.param.MetricType;
import io.milvus.param.IndexType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.*;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.response.SearchResultsWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Service
public class VectorServiceImpl implements VectorService {

    @Resource
    private IBlogService blogService;

    @Resource
    private MilvusServiceClient milvusServiceClient;

    @Resource
    private IAiRuleDocService aiRuleDocService;

    @Resource
    private EmbeddingService embeddingService;

    @Override
    public void createBlogCollectionIfNotExists() {
        String collection = MilvusVectorConstants.BLOG_COLLECTION;

        if (collectionExists(collection)) {
            log.info("Blog collection already exists: {}", collection);
            return;
        }

        FieldType idField = FieldType.newBuilder()
                .withName(MilvusVectorConstants.FIELD_ID)
                .withDataType(DataType.Int64)
                .withPrimaryKey(true)
                .withAutoID(false)
                .build();

        FieldType docIdField = FieldType.newBuilder()
                .withName(MilvusVectorConstants.FIELD_DOC_ID)
                .withDataType(DataType.Int64)
                .build();

        FieldType shopIdField = FieldType.newBuilder()
                .withName(MilvusVectorConstants.FIELD_SHOP_ID)
                .withDataType(DataType.Int64)
                .build();

        FieldType titleField = FieldType.newBuilder()
                .withName(MilvusVectorConstants.FIELD_TITLE)
                .withDataType(DataType.VarChar)
                .withMaxLength(512)
                .build();

        FieldType contentField = FieldType.newBuilder()
                .withName(MilvusVectorConstants.FIELD_CONTENT)
                .withDataType(DataType.VarChar)
                .withMaxLength(2048)
                .build();

        FieldType typeField = FieldType.newBuilder()
                .withName(MilvusVectorConstants.FIELD_TYPE)
                .withDataType(DataType.VarChar)
                .withMaxLength(64)
                .build();

        FieldType vectorField = FieldType.newBuilder()
                .withName(MilvusVectorConstants.FIELD_VECTOR)
                .withDataType(DataType.FloatVector)
                .withDimension(MilvusVectorConstants.VECTOR_DIM)
                .build();

        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(collection)
                .withDescription("探店 blog 向量库")
                .withShardsNum(1)
                .addFieldType(idField)
                .addFieldType(docIdField)
                .addFieldType(shopIdField)
                .addFieldType(titleField)
                .addFieldType(contentField)
                .addFieldType(typeField)
                .addFieldType(vectorField)
                .build();

        R<RpcStatus> createResult = milvusServiceClient.createCollection(createParam);
        if (createResult.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("创建 Blog collection 失败: " + createResult.getMessage());
        }

        CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                .withCollectionName(collection)
                .withFieldName(MilvusVectorConstants.FIELD_VECTOR)
                .withIndexType(IndexType.FLAT)
                .withMetricType(MetricType.COSINE)
                .build();

        R<RpcStatus> indexResult = milvusServiceClient.createIndex(indexParam);
        if (indexResult.getStatus() != R.Status.Success.getCode()) {
            log.warn("创建 Blog 向量索引失败: {}", indexResult.getMessage());
        }

        log.info("Blog collection 创建成功: {}", collection);
    }

    @Override
    public void dropBlogCollection() {
        String collection = MilvusVectorConstants.BLOG_COLLECTION;

        if (!collectionExists(collection)) {
            log.info("Blog collection 不存在，跳过删除");
            return;
        }

        milvusServiceClient.dropCollection(
                DropCollectionParam.newBuilder()
                        .withCollectionName(collection)
                        .build()
        );

        log.info("Blog collection 已删除: {}", collection);
    }

    @Override
    public void syncBlogDocumentsToVectorStore() {
        createBlogCollectionIfNotExists();

        List<Blog> blogs = blogService.list();
        if (blogs == null || blogs.isEmpty()) {
            log.warn("Blog 数据为空，跳过同步");
            return;
        }

        List<Long> ids = new ArrayList<>();
        List<Long> docIds = new ArrayList<>();
        List<Long> shopIds = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        List<String> types = new ArrayList<>();
        List<List<Float>> vectors = new ArrayList<>();

        long autoId = 1L;

        for (Blog blog : blogs) {
            try {
                String text = buildBlogText(blog);
                if (isBlank(text) || text.length() < 10) {
                    continue;
                }

                List<Float> rawVector = embeddingService.embed(text);
                List<Float> vector = rawVector;

                if (vector == null || vector.isEmpty()) {
                    log.warn("Blog embedding 为空, blogId={}", blog.getId());
                    continue;
                }

                if (vector.size() != MilvusVectorConstants.VECTOR_DIM) {
                    log.warn("Blog embedding 维度不匹配, blogId={}, actual={}, expected={}",
                            blog.getId(), vector.size(), MilvusVectorConstants.VECTOR_DIM);
                    continue;
                }

                ids.add(autoId++);
                docIds.add(blog.getId());
                shopIds.add(blog.getShopId() == null ? 0L : blog.getShopId());
                titles.add(safe(blog.getTitle()));
                contents.add(truncate(safe(blog.getContent()), 800));
                types.add("BLOG");
                vectors.add(vector);

            } catch (Exception e) {
                log.error("Blog 向量化失败, blogId={}", blog.getId(), e);
            }
        }

        if (ids.isEmpty()) {
            log.warn("没有可插入的 Blog 向量数据");
            return;
        }

        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field(MilvusVectorConstants.FIELD_ID, ids));
        fields.add(new InsertParam.Field(MilvusVectorConstants.FIELD_DOC_ID, docIds));
        fields.add(new InsertParam.Field(MilvusVectorConstants.FIELD_SHOP_ID, shopIds));
        fields.add(new InsertParam.Field(MilvusVectorConstants.FIELD_TITLE, titles));
        fields.add(new InsertParam.Field(MilvusVectorConstants.FIELD_CONTENT, contents));
        fields.add(new InsertParam.Field(MilvusVectorConstants.FIELD_TYPE, types));
        fields.add(new InsertParam.Field(MilvusVectorConstants.FIELD_VECTOR, vectors));

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(MilvusVectorConstants.BLOG_COLLECTION)
                .withFields(fields)
                .build();

        R<MutationResult> insertResult = milvusServiceClient.insert(insertParam);
        if (insertResult.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Blog 数据插入 Milvus 失败: " + insertResult.getMessage());
        }

        log.info("Blog 向量同步完成, total={}, success={}", blogs.size(), ids.size());
    }

    @Override
    public List<AiEvidenceDTO> searchBlogByVector(String question, Long shopId, int topK) {
        if (isBlank(question)) {
            return Collections.emptyList();
        }

        try {
            List<Float> rawQueryVector = embeddingService.embed(question);
            List<Float> queryVector = rawQueryVector;

            if (queryVector == null || queryVector.isEmpty()) {
                log.warn("Blog 查询向量为空, question={}", question);
                return Collections.emptyList();
            }

            if (queryVector.size() != MilvusVectorConstants.VECTOR_DIM) {
                log.warn("Blog 查询向量维度不匹配, actual={}, expected={}",
                        queryVector.size(), MilvusVectorConstants.VECTOR_DIM);
                return Collections.emptyList();
            }

            milvusServiceClient.loadCollection(
                    LoadCollectionParam.newBuilder()
                            .withCollectionName(MilvusVectorConstants.BLOG_COLLECTION)
                            .build()
            );

            SearchParam.Builder searchBuilder = SearchParam.newBuilder()
                    .withCollectionName(MilvusVectorConstants.BLOG_COLLECTION)
                    .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                    .withVectorFieldName(MilvusVectorConstants.FIELD_VECTOR)
                    .withVectors(Collections.singletonList(queryVector))
                    .withTopK(topK * 2)
                    .withMetricType(MetricType.COSINE)
                    .withOutFields(Arrays.asList(
                            MilvusVectorConstants.FIELD_DOC_ID,
                            MilvusVectorConstants.FIELD_SHOP_ID,
                            MilvusVectorConstants.FIELD_TITLE,
                            MilvusVectorConstants.FIELD_CONTENT,
                            MilvusVectorConstants.FIELD_TYPE
                    ));

            // 按 shopId 过滤
            if (shopId != null) {
                searchBuilder.withExpr(MilvusVectorConstants.FIELD_SHOP_ID + " == " + shopId);
            }

            R<SearchResults> response = milvusServiceClient.search(searchBuilder.build());
            if (response.getStatus() != R.Status.Success.getCode()) {
                log.error("Blog 向量检索失败, status={}, message={}",
                        response.getStatus(), response.getMessage());
                return Collections.emptyList();
            }

            SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
            List<AiEvidenceDTO> result = parseBlogSearchResult(wrapper, topK);

            log.info("Blog 向量检索完成, question={}, shopId={}, topK={}, hitCount={}",
                    question, shopId, topK, result.size());

            return result;

        } catch (Exception e) {
            log.error("Blog 向量检索异常, question={}, shopId={}", question, shopId, e);
            return Collections.emptyList();
        }
    }

    private List<AiEvidenceDTO> parseBlogSearchResult(SearchResultsWrapper wrapper, int maxCount) {
        List<AiEvidenceDTO> evidenceList = new ArrayList<>();
        try {
            List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
            if (scores == null || scores.isEmpty()) {
                return Collections.emptyList();
            }

            for (int i = 0; i < Math.min(scores.size(), maxCount); i++) {
                AiEvidenceDTO evidence = new AiEvidenceDTO();
                evidence.setSourceType("BLOG");
                evidence.setSourceId(parseLong(getFieldValue(wrapper, MilvusVectorConstants.FIELD_DOC_ID, i)));
                evidence.setTitle(getFieldValue(wrapper, MilvusVectorConstants.FIELD_TITLE, i));
                evidence.setContent(getFieldValue(wrapper, MilvusVectorConstants.FIELD_CONTENT, i));
                evidenceList.add(evidence);

                log.debug("Blog 命中: title={}, score={}",
                        evidence.getTitle(), scores.get(i).getScore());
            }
        } catch (Exception e) {
            log.error("解析 Blog 检索结果异常", e);
        }
        return evidenceList;
    }
    private String buildBlogText(Blog blog) {
        StringBuilder sb = new StringBuilder();
        if (isNotBlank(blog.getTitle())) {
            sb.append(blog.getTitle()).append("。");
        }
        if (isNotBlank(blog.getContent())) {
            sb.append(blog.getContent());
        }
        return truncate(sb.toString().trim(), 1000);
    }
    // ========================= 创建 FAQ Collection =========================

    @Override
    public void createFaqCollectionIfNotExists() {
        String collection = MilvusVectorConstants.FAQ_COLLECTION;

        // 检查是否已存在
        if (collectionExists(collection)) {
            log.info("FAQ collection already exists: {}", collection);
            return;
        }

        // 定义字段
        FieldType idField = FieldType.newBuilder()
                .withName(MilvusVectorConstants.FIELD_ID)
                .withDataType(DataType.Int64)
                .withPrimaryKey(true)
                .withAutoID(false)
                .build();

        FieldType docIdField = FieldType.newBuilder()
                .withName(MilvusVectorConstants.FIELD_DOC_ID)
                .withDataType(DataType.Int64)
                .build();

        FieldType titleField = FieldType.newBuilder()
                .withName(MilvusVectorConstants.FIELD_TITLE)
                .withDataType(DataType.VarChar)
                .withMaxLength(512)
                .build();

        FieldType contentField = FieldType.newBuilder()
                .withName(MilvusVectorConstants.FIELD_CONTENT)
                .withDataType(DataType.VarChar)
                .withMaxLength(2048)
                .build();

        FieldType typeField = FieldType.newBuilder()
                .withName(MilvusVectorConstants.FIELD_TYPE)
                .withDataType(DataType.VarChar)
                .withMaxLength(64)
                .build();

        FieldType vectorField = FieldType.newBuilder()
                .withName(MilvusVectorConstants.FIELD_VECTOR)
                .withDataType(DataType.FloatVector)
                .withDimension(MilvusVectorConstants.VECTOR_DIM)
                .build();

        // 创建 collection
        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(collection)
                .withDescription("FAQ 规则文档向量库")
                .withShardsNum(1)
                .addFieldType(idField)
                .addFieldType(docIdField)
                .addFieldType(titleField)
                .addFieldType(contentField)
                .addFieldType(typeField)
                .addFieldType(vectorField)
                .build();

        R<RpcStatus> createResult = milvusServiceClient.createCollection(createParam);
        if (createResult.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("创建 FAQ collection 失败: " + createResult.getMessage());
        }

        // 创建向量索引
        CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                .withCollectionName(collection)
                .withFieldName(MilvusVectorConstants.FIELD_VECTOR)
                .withIndexType(IndexType.FLAT)
                .withMetricType(MetricType.COSINE)
                .build();

        R<RpcStatus> indexResult = milvusServiceClient.createIndex(indexParam);
        if (indexResult.getStatus() != R.Status.Success.getCode()) {
            log.warn("创建 FAQ 向量索引失败: {}", indexResult.getMessage());
        }

        log.info("FAQ collection 创建成功: {}", collection);
    }

    // ========================= 删除 FAQ Collection =========================

    @Override
    public void dropFaqCollection() {
        String collection = MilvusVectorConstants.FAQ_COLLECTION;

        if (!collectionExists(collection)) {
            log.info("FAQ collection 不存在，跳过删除");
            return;
        }

        milvusServiceClient.dropCollection(
                DropCollectionParam.newBuilder()
                        .withCollectionName(collection)
                        .build()
        );

        log.info("FAQ collection 已删除: {}", collection);
    }

    // ========================= 同步 FAQ 文档 =========================

    @Override
    public void syncFaqDocumentsToVectorStore() {
        createFaqCollectionIfNotExists();

        List<AiRuleDoc> docs = aiRuleDocService.list();
        if (docs == null || docs.isEmpty()) {
            log.warn("FAQ 文档为空，跳过同步");
            return;
        }

        List<Long> ids = new ArrayList<>();
        List<Long> docIds = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        List<String> types = new ArrayList<>();
        List<List<Float>> vectors = new ArrayList<>();

        long autoId = 1L;

        for (AiRuleDoc doc : docs) {
            try {
                String text = buildFaqText(doc);
                if (text == null || text.trim().isEmpty()) {
                    continue;
                }

                List<Float> vector = embeddingService.embed(text);
                if (vector == null || vector.isEmpty()) {
                    log.warn("FAQ embedding 为空, docId={}", doc.getId());
                    continue;
                }

                if (vector.size() != MilvusVectorConstants.VECTOR_DIM) {
                    log.warn("FAQ embedding 维度不匹配, docId={}, actual={}, expected={}",
                            doc.getId(), vector.size(), MilvusVectorConstants.VECTOR_DIM);
                    continue;
                }

                ids.add(autoId++);
                docIds.add(doc.getId());
                titles.add(safe(doc.getTitle()));
                contents.add(truncate(safe(doc.getContent()), 500));
                types.add("RULE_DOC");
                vectors.add(vector);

            } catch (Exception e) {
                log.error("FAQ 向量化失败, docId={}", doc.getId(), e);
            }
        }

        if (ids.isEmpty()) {
            log.warn("没有可插入的 FAQ 向量数据");
            return;
        }

        // 批量插入
        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field(MilvusVectorConstants.FIELD_ID, ids));
        fields.add(new InsertParam.Field(MilvusVectorConstants.FIELD_DOC_ID, docIds));
        fields.add(new InsertParam.Field(MilvusVectorConstants.FIELD_TITLE, titles));
        fields.add(new InsertParam.Field(MilvusVectorConstants.FIELD_CONTENT, contents));
        fields.add(new InsertParam.Field(MilvusVectorConstants.FIELD_TYPE, types));
        fields.add(new InsertParam.Field(MilvusVectorConstants.FIELD_VECTOR, vectors));

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(MilvusVectorConstants.FAQ_COLLECTION)
                .withFields(fields)
                .build();

        R<MutationResult> insertResult = milvusServiceClient.insert(insertParam);
        if (insertResult.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("FAQ 数据插入 Milvus 失败: " + insertResult.getMessage());
        }

        log.info("FAQ 向量同步完成, total={}, success={}", docs.size(), ids.size());
    }

    // ========================= FAQ 向量检索 =========================

    @Override
    public List<AiEvidenceDTO> searchFaqByVector(String question, int topK) {
        if (question == null || question.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            List<Float> queryVector = embeddingService.embed(question);
            if (queryVector == null || queryVector.isEmpty()) {
                log.warn("查询向量为空, question={}", question);
                return Collections.emptyList();
            }

            if (queryVector.size() != MilvusVectorConstants.VECTOR_DIM) {
                log.warn("查询向量维度不匹配, actual={}, expected={}",
                        queryVector.size(), MilvusVectorConstants.VECTOR_DIM);
                return Collections.emptyList();
            }

            // 加载 collection 到内存
            milvusServiceClient.loadCollection(
                    LoadCollectionParam.newBuilder()
                            .withCollectionName(MilvusVectorConstants.FAQ_COLLECTION)
                            .build()
            );

            // 构建检索参数
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(MilvusVectorConstants.FAQ_COLLECTION)
                    .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                    .withVectorFieldName(MilvusVectorConstants.FIELD_VECTOR)
                    .withVectors(Collections.singletonList(queryVector))
                    .withTopK(topK)
                    .withMetricType(MetricType.COSINE)
                    .withOutFields(Arrays.asList(
                            MilvusVectorConstants.FIELD_DOC_ID,
                            MilvusVectorConstants.FIELD_TITLE,
                            MilvusVectorConstants.FIELD_CONTENT,
                            MilvusVectorConstants.FIELD_TYPE
                    ))
                    .build();

            R<SearchResults> response = milvusServiceClient.search(searchParam);
            if (response.getStatus() != R.Status.Success.getCode()) {
                log.error("FAQ 向量检索失败, status={}, message={}",
                        response.getStatus(), response.getMessage());
                return Collections.emptyList();
            }

            // 解析结果
            SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
            List<AiEvidenceDTO> result = parseSearchResult(wrapper, topK);

            log.info("FAQ 向量检索完成, question={}, topK={}, hitCount={}",
                    question, topK, result.size());

            return result;

        } catch (Exception e) {
            log.error("FAQ 向量检索异常, question={}", question, e);
            return Collections.emptyList();
        }
    }

    // ========================= 公共方法 =========================

    private boolean collectionExists(String collectionName) {
        R<Boolean> result = milvusServiceClient.hasCollection(
                HasCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .build()
        );
        return Boolean.TRUE.equals(result.getData());
    }

    private List<AiEvidenceDTO> parseSearchResult(SearchResultsWrapper wrapper, int maxCount) {
        List<AiEvidenceDTO> evidenceList = new ArrayList<>();
        try {
            List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
            if (scores == null || scores.isEmpty()) {
                return Collections.emptyList();
            }

            for (int i = 0; i < Math.min(scores.size(), maxCount); i++) {
                AiEvidenceDTO evidence = new AiEvidenceDTO();
                evidence.setSourceType(getFieldValue(wrapper, MilvusVectorConstants.FIELD_TYPE, i));
                evidence.setSourceId(parseLong(getFieldValue(wrapper, MilvusVectorConstants.FIELD_DOC_ID, i)));
                evidence.setTitle(getFieldValue(wrapper, MilvusVectorConstants.FIELD_TITLE, i));
                evidence.setContent(getFieldValue(wrapper, MilvusVectorConstants.FIELD_CONTENT, i));
                evidenceList.add(evidence);

                log.debug("FAQ 命中: title={}, score={}",
                        evidence.getTitle(), scores.get(i).getScore());
            }
        } catch (Exception e) {
            log.error("解析检索结果异常", e);
        }
        return evidenceList;
    }

    private String getFieldValue(SearchResultsWrapper wrapper, String fieldName, int index) {
        try {
            List<?> fieldData = wrapper.getFieldData(fieldName, 0);
            if (fieldData != null && index < fieldData.size()) {
                Object val = fieldData.get(index);
                return val == null ? "" : String.valueOf(val);
            }
        } catch (Exception e) {
            log.warn("获取字段值失败, field={}", fieldName);
        }
        return "";
    }

    private String buildFaqText(AiRuleDoc doc) {
        StringBuilder sb = new StringBuilder();
        if (doc.getTitle() != null && !doc.getTitle().trim().isEmpty()) {
            sb.append(doc.getTitle()).append("。");
        }
        if (doc.getContent() != null && !doc.getContent().trim().isEmpty()) {
            sb.append(doc.getContent());
        }
        return sb.toString().trim();
    }

    private String safe(String str) {
        return str == null ? "" : str;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private Long parseLong(String str) {
        try {
            return Long.valueOf(str);
        } catch (Exception e) {
            return null;
        }
    }
    private boolean isNotBlank(String str) {
        return str != null && !str.trim().isEmpty();
    }
    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}