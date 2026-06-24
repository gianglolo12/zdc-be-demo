package com.zdc.order.web.dto;

/** A single order line request (product + quantity). Validated in the service. */
public record OrderItemInput(Long productId, Integer quantity) {
}
