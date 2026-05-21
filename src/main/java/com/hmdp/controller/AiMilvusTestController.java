package com.hmdp.controller;

import com.hmdp.dto.Result;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.R;
import io.milvus.param.collection.ShowCollectionsParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/ai/test")
public class AiMilvusTestController {

    @Resource
    private MilvusServiceClient milvusServiceClient;

    @GetMapping("/milvus")
    public Result testMilvus() {
        try {
            R<?> result = milvusServiceClient.showCollections(
                    ShowCollectionsParam.newBuilder().build()
            );

            if (result == null) {
                return Result.fail("Milvus 返回为空");
            }

            if (result.getStatus() != 0) {
                return Result.fail("Milvus 调用失败: " + result.getMessage());
            }

            return Result.ok("Milvus 连接成功");
        } catch (Exception e) {
            return Result.fail("Milvus 连接失败: " + e.getMessage());
        }
    }
}