package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModerationResult {
    private Boolean approved;
    private String reason;
    private String riskLevel;
    private String category;

    public boolean isApproved() {
        return Boolean.TRUE.equals(approved);
    }
}