package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignInfoDTO {
    private Integer consecutiveDays;
    private Integer totalSignDays;
    private Boolean signedToday;

    public SignInfoDTO(int consecutiveDays, int dayOfMonth) {
        this.consecutiveDays = consecutiveDays;
        this.totalSignDays = dayOfMonth;
        this.signedToday = consecutiveDays > 0;
    }
}