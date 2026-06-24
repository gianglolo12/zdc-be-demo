package com.zdc.order.gateway;

/** Product (mệnh giá) as exposed by BO Service. Amounts are whole VND. */
public record ProductView(
        Long id,
        String name,
        String type,
        Long denomination,
        Long unitPrice,
        String imageUrl,
        String countryCode,
        boolean enabled) {
}
