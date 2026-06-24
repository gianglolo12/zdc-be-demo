package com.zdc.order.web.dto;

/** FS-003 per-line pricing breakdown. */
public record LineItemBreakdown(
        Long productId,
        Integer quantity,
        Long unitPriceOriginal,
        Long unitPriceDiscounted,
        Long lineSubtotal,
        Long lineDiscount,
        Long lineTotal) {
}
