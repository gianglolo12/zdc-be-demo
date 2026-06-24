package com.zdc.order.gateway;

/** Payment channel as exposed by BO Service. Amounts are whole VND. */
public record ChannelView(
        Long id,
        String paymentMethod,
        String paymentProvider,
        Long minAmount,
        Long maxAmount,
        String countryCode,
        boolean enabled) {
}
