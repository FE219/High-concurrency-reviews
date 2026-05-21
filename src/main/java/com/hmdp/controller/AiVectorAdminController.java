package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.rag.service.VectorService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/ai/admin/vector")
public class AiVectorAdminController {

    @Resource
    private VectorService vectorService;

    @PostMapping("/faq/collection")
    public Result createFaqCollection() {
        vectorService.createFaqCollectionIfNotExists();
        return Result.ok("FAQ 向量集合创建完成");
    }

    @PostMapping("/faq/drop")
    public Result dropFaqCollection() {
        vectorService.dropFaqCollection();
        return Result.ok("FAQ 向量集合删除完成");
    }

    @PostMapping("/faq/sync")
    public Result syncFaqVectors() {
        vectorService.syncFaqDocumentsToVectorStore();
        return Result.ok("FAQ 文档向量同步完成");
    }

    @GetMapping("/faq/search")
    public Result searchFaqVectors(
            @RequestParam("q") String question,
            @RequestParam(value = "topK", required = false, defaultValue = "3") Integer topK) {
        return Result.ok(vectorService.searchFaqByVector(question, topK));
    }

    @PostMapping("/blog/collection")
    public Result createBlogCollection() {
        vectorService.createBlogCollectionIfNotExists();
        return Result.ok("Blog 向量集合创建完成");
    }

    @PostMapping("/blog/drop")
    public Result dropBlogCollection() {
        vectorService.dropBlogCollection();
        return Result.ok("Blog 向量集合删除完成");
    }

    @PostMapping("/blog/sync")
    public Result syncBlogVectors() {
        vectorService.syncBlogDocumentsToVectorStore();
        return Result.ok("Blog 文档向量同步完成");
    }

    @GetMapping("/blog/search")
    public Result searchBlogVectors(
            @RequestParam("q") String question,
            @RequestParam(value = "shopId", required = false) Long shopId,
            @RequestParam(value = "topK", required = false, defaultValue = "3") Integer topK) {
        return Result.ok(vectorService.searchBlogByVector(question, shopId, topK));
    }
}