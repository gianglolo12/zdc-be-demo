package com.zdc.order.web.dto;

import java.time.Instant;

/** FS-004 response envelope. */
public record CreateOrderResponse(Data data) {

    public record Data(
            Long orderId,
            String status,
            Long subtotal,
            Long grandTotal,
            String currency,
            String paymentUrl,
            String qrCode,
            String deeplink,
            Instant expiredAt,
            Long referenceId,
            Instant createdAt) {
    }
}
