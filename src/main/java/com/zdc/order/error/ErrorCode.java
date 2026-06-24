package com.zdc.order.error;

import org.springframework.http.HttpStatus;

/**
 * F07 business/error catalog (FRS §VIII). The api-contract governs shape: errors
 * use the {@code {code,message,details}} envelope, and several "business errors"
 * are returned with HTTP 200 (not 4xx) so the FE can render specific UI.
 */
public enum ErrorCode {

    ERR_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ"),
    ERR_AGENT_NOT_ACTIVE(HttpStatus.FORBIDDEN, "Tài khoản chưa được kích hoạt"),
    ERR_INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "Vui lòng nhập số lượng hợp lệ (số nguyên dương)"),
    ERR_PRODUCT_UNAVAILABLE(HttpStatus.BAD_REQUEST, "Mệnh giá hoặc kênh thanh toán không khả dụng"),
    ERR_QUANTITY_EXCEEDED(HttpStatus.BAD_REQUEST, "Số lượng vượt giới hạn cho phép"),
    ERR_CREDIT_INSUFFICIENT(HttpStatus.OK, "Đơn hàng vượt hạn mức giao dịch"),
    ERR_CREDIT_EXHAUSTED(HttpStatus.OK, "Bạn đã sử dụng hết hạn mức giao dịch trong kỳ này"),
    ERR_PAYMENT_GATEWAY(HttpStatus.OK, "Cổng thanh toán đang lỗi, vui lòng thử lại"),
    ERR_CONCURRENT_OPERATION(HttpStatus.OK, "Thao tác đang được xử lý, vui lòng thử lại"),
    ERR_NO_PRODUCTS(HttpStatus.OK, "Hiện không có mệnh giá nào khả dụng"),
    ERR_INTERNAL(HttpStatus.BAD_GATEWAY, "Lỗi hệ thống, vui lòng thử lại");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
