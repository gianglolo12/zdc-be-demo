package com.zdc.order.web.dto;

/** FS-002 channel item (api-contract PaymentChannel schema). */
public record PaymentChannelDto(
        Long id,
        String paymentMethod,
        String paymentProvider,
        Long minAmount,
        Long maxAmount,
        String countryCode,
        boolean enabled) {
}
