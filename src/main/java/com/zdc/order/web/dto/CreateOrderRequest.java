package com.zdc.order.web.dto;

import java.util.List;

/** FS-004 request. {@code paymentChannelId} required (validated in the service). */
public record CreateOrderRequest(List<OrderItemInput> items, Long paymentChannelId) {
}
