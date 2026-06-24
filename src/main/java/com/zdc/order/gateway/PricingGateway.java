package com.zdc.order.gateway;

import com.zdc.order.domain.DiscountScope;
import com.zdc.order.domain.DiscountType;

import java.math.BigDecimal;
import java.util.List;

/** Port to the stateless Pricing Service (ADR-016/017). */
public interface PricingGateway {

    /** Compute per-channel pricing for the given items + candidate channels. */
    List<ChannelPricing> calculate(PricingRequest request);

    record PricingRequest(Long agentId, String countryCode, List<Item> items, List<Channel> channels) {
        public record Item(Long productId, Long unitPrice, Integer quantity) {
        }

        public record Channel(Long id, String paymentMethod, String paymentProvider,
                              Long minAmount, Long maxAmount) {
        }
    }

    record ChannelPricing(
            Long channelId,
            Long subtotal,
            Long itemDiscountTotal,
            Long orderDiscount,
            Long channelFee,
            Long grandTotal,
            List<Line> lines,
            List<AppliedDiscount> appliedDiscounts) {

        public record Line(
                Long productId,
                Integer quantity,
                Long unitPriceOriginal,
                Long unitPriceDiscounted,
                Long lineSubtotal,
                Long lineDiscount,
                Long lineTotal) {
        }

        public record AppliedDiscount(
                Long policyId,
                String policyName,
                DiscountScope scope,
                DiscountType discountType,
                BigDecimal discountValue,
                Long appliedAmount,
                Long targetItemId) {
        }
    }
}
