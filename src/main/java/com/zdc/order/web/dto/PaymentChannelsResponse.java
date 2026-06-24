package com.zdc.order.web.dto;

import java.util.List;

/** FS-002 response envelope ({data:[...]}). */
public record PaymentChannelsResponse(List<PaymentChannelDto> data) {
}
