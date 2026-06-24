package com.zdc.order.web.dto;

import java.util.List;

/** FS-003 request. {@code paymentChannelId} optional (preview all channels if absent). */
public record PreviewOrderRequest(List<OrderItemInput> items, Long paymentChannelId) {
}
