package com.zdc.order.web.dto;

import java.math.BigDecimal;

/** FS-003 applied-discount breakdown. */
public record AppliedDiscountDto(
        Long policyId,
        String policyName,
        String scope,
        String discountType,
        BigDecimal discountValue,
        Long appliedAmount,
        Long targetItemId) {
}
