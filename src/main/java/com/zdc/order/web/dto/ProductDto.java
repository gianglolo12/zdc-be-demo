package com.zdc.order.web.dto;

/** FS-001 product item (api-contract Product schema). */
public record ProductDto(
        Long id,
        String name,
        String type,
        Long denomination,
        Long unitPrice,
        String imageUrl,
        String countryCode,
        boolean enabled) {
}
