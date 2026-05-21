package com.hmdp.dto.response;

import lombok.Data;

@Data
public class ShopDetailCardDTO {
    private Long shopId;
    private String shopName;
    private Double score;
    private String avgPriceText;
    private String area;
    private String address;
    private String openHours;
}