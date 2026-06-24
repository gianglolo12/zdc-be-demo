package com.zdc.order.web;

import com.zdc.order.error.ErrorCode;
import com.zdc.order.error.OrderException;
import com.zdc.order.web.dto.BusinessError;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps {@link OrderException} to the api-contract BusinessError envelope. Scoped
 * to the order web package so it does not interfere with the generic RFC-7807
 * handler used elsewhere ({@code common.web.GlobalExceptionHandler}).
 */
@Order(0)
@RestControllerAdvice(basePackages = "com.zdc.order.web")
public class OrderExceptionHandler {

    @ExceptionHandler(OrderException.class)
    public ResponseEntity<BusinessError> onOrderException(OrderException ex) {
        BusinessError body = new BusinessError(ex.code().name(), ex.getMessage(), ex.details());
        return ResponseEntity.status(ex.status()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BusinessError> onUnexpected(Exception ex) {
        BusinessError body = new BusinessError(ErrorCode.ERR_INTERNAL.name(), ErrorCode.ERR_INTERNAL.message());
        return ResponseEntity.status(ErrorCode.ERR_INTERNAL.status()).body(body);
    }
}
