package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.dto.request.AiChatRequest;
import com.hmdp.dto.response.AiChatResponse;
import com.hmdp.service.AiChatService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/ai")
public class AiChatController {

    @Resource
    private AiChatService aiChatService;

    @PostMapping("/chat")
    public Result chat(@RequestBody @Valid AiChatRequest request) {
        if (request == null || request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return Result.fail("message不能为空");
        }
        if (request.getSessionId() == null || request.getSessionId().trim().isEmpty()) {
            request.setSessionId(UUID.randomUUID().toString());
        }

        AiChatResponse response = aiChatService.chat(request);
        return Result.ok(response);
    }
}