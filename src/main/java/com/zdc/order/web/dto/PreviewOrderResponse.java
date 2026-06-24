package com.zdc.order.web.dto;

import java.util.List;

/** FS-003 response envelope. */
public record PreviewOrderResponse(Data data) {

    public record Data(
            Long subtotal,
            String currency,
            Long availableCredit,
            List<ChannelPricingDto> availableChannels) {
    }
}
