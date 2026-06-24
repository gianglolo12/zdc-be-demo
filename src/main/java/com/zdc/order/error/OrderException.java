package com.zdc.order.error;

import org.springframework.http.HttpStatus;

import java.util.Map;

/** Domain exception carrying an {@link ErrorCode} and optional detail fields. */
public class OrderException extends RuntimeException {

    private final ErrorCode code;
    private final HttpStatus status;
    private final transient Map<String, Object> details;

    public OrderException(ErrorCode code) {
        this(code, code.message(), null, code.status());
    }

    public OrderException(ErrorCode code, String message) {
        this(code, message, null, code.status());
    }

    public OrderException(ErrorCode code, String message, Map<String, Object> details) {
        this(code, message, details, code.status());
    }

    public OrderException(ErrorCode code, String message, Map<String, Object> details, HttpStatus status) {
        super(message);
        this.code = code;
        this.details = details;
        this.status = status;
    }

    public ErrorCode code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public Map<String, Object> details() {
        return details;
    }
}
