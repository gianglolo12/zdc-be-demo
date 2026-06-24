package com.zdc.order.web.dto;

import java.util.List;

/** FS-003 per-channel pricing + credit/eligibility flags. */
public record ChannelPricingDto(
        Long id,
        String paymentMethod,
        String paymentProvider,
        boolean fitsAmount,
        Long grandTotal,
        Long itemDiscountTotal,
        Long orderDiscount,
        Long channelFee,
        boolean exceedCreditLimit,
        List<LineItemBreakdown> lines,
        List<AppliedDiscountDto> appliedDiscounts) {
}
