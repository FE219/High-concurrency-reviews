package com.hmdp.constant;

public enum ErrorCode {
    STOCK_INSUFFICIENT("STOCK_INSUFFICIENT", "库存不足"),
    DUPLICATE_ORDER("DUPLICATE_ORDER", "不能重复下单"),
    SHOP_NOT_FOUND("SHOP_NOT_FOUND", "店铺不存在"),
    VOUCHER_NOT_FOUND("VOUCHER_NOT_FOUND", "优惠券不存在"),
    AI_TIMEOUT("AI_TIMEOUT", "AI服务响应超时"),
    AI_CIRCUIT_OPEN("AI_CIRCUIT_OPEN", "AI服务暂时不可用"),
    VALIDATION_ERROR("VALIDATION_ERROR", "参数校验失败"),
    INTERNAL_ERROR("INTERNAL_ERROR", "服务器内部错误");

    private final String code;
    private final String defaultMsg;

    ErrorCode(String code, String defaultMsg) {
        this.code = code;
        this.defaultMsg = defaultMsg;
    }
    public String getCode() { return code; }
    public String getDefaultMsg() { return defaultMsg; }
}
