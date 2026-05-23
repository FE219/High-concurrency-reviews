package com.hmdp.dto;

import lombok.Data;
import org.slf4j.MDC;

@Data
public class Result {
    private Boolean success;
    private String code;
    private String errorMsg;
    private String detail;
    private String traceId;
    private Object data;
    private Long total;

    private Result() {}

    public static Result ok() {
        Result r = new Result();
        r.success = true;
        r.traceId = MDC.get("traceId");
        return r;
    }

    public static Result ok(Object data) {
        Result r = ok();
        r.data = data;
        return r;
    }

    public static Result ok(java.util.List<?> data, Long total) {
        Result r = ok(data);
        r.total = total;
        return r;
    }

    public static Result fail(String errorMsg) {
        Result r = new Result();
        r.success = false;
        r.code = "BUSINESS_ERROR";
        r.errorMsg = errorMsg;
        r.traceId = MDC.get("traceId");
        return r;
    }

    public static Result fail(String code, String errorMsg, String detail) {
        Result r = new Result();
        r.success = false;
        r.code = code;
        r.errorMsg = errorMsg;
        r.detail = detail;
        r.traceId = MDC.get("traceId");
        return r;
    }
}
