package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.rag.service.EmbeddingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/ai/test")
public class AiEmbeddingTestController {

    @Resource
    private EmbeddingService embeddingService;

    @GetMapping("/embedding")
    public Result testEmbedding(@RequestParam("text") String text) {
        List<Float> vector = embeddingService.embed(text);
        if (vector == null || vector.isEmpty()) {
            return Result.fail("embedding 失败，请检查 API Key 和模型名称");
        }
        return Result.ok("embedding 成功，dim=" + vector.size());
    }
}