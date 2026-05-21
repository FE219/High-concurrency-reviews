package com.hmdp.dto;
import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private String conversationId;
    private Double longitude;
    private Double latitude;
}