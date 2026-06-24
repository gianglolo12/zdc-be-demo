package com.zdc.order.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** RFC-deviation per api-contract: business error envelope {code,message,details}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BusinessError(String code, String message, Map<String, Object> details) {

    public BusinessError(String code, String message) {
        this(code, message, null);
    }
}
