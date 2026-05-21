package com.hmdp.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class AiChatResponse {
    private String reply;
    private String intent;
    private List<RecommendationItemDTO> recommendations;
    private List<String> suggestions;
    private ShopDetailCardDTO shopDetail;
    private String answer;
    private String sessionId;
    private List<String> knowledgeSources;
    private List<String> hitTitles;
    private Boolean fallback;
}