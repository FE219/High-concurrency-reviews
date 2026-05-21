package com.hmdp.rag.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.rag.service.EmbeddingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${spring.ai.dashscope.embedding.enabled:false}")
    private Boolean enabled;

    @Value("${spring.ai.dashscope.embedding.api-key:}")
    private String apiKey;

    @Value("${spring.ai.dashscope.embedding.base-url:}")
    private String baseUrl;

    @Value("${spring.ai.dashscope.embedding.options.model:text-embedding-v3}")
    private String modelName;

    @Resource
    private RestTemplate restTemplate;

    @Override
    public List<Float> embed(String text) {
        if (StrUtil.isBlank(text)) {
            return new ArrayList<>();
        }

        // 如果 embedding 没开启，直接抛错或降级
        if (Boolean.FALSE.equals(enabled)) {
            throw new RuntimeException("Embedding 未开启，请检查 ai.embedding.enabled 配置");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelName);

            Map<String, Object> input = new HashMap<>();
            List<String> texts = new ArrayList<>();
            texts.add(text);
            input.put("texts", texts);

            requestBody.put("input", input);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            String body = response.getBody();
            if (StrUtil.isBlank(body)) {
                throw new RuntimeException("DashScope embedding 返回为空");
            }

            JsonNode root = MAPPER.readTree(body);
            JsonNode embeddingsNode = root.path("output").path("embeddings");

            if (!embeddingsNode.isArray() || embeddingsNode.size() == 0) {
                throw new RuntimeException("DashScope embedding 返回格式异常：" + body);
            }

            JsonNode embeddingNode = embeddingsNode.get(0).path("embedding");
            List<Float> vector = new ArrayList<>();

            for (JsonNode node : embeddingNode) {
                vector.add(node == null ? 0F : node.floatValue());
            }

            return vector;
        } catch (Exception e) {
            throw new RuntimeException("调用 DashScope embedding 失败", e);
        }
    }
}