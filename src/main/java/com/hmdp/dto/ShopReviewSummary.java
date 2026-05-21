package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopReviewSummary {
    private String overallSummary;
    private List<String> pros;
    private List<String> cons;
    private Map<String, Double> scores;
    private List<String> recommendedDishes;
    private String bestTimeToVisit;
}