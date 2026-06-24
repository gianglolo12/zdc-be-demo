package com.zdc.order.domain;

/**
 * Order FSM (ADR-018, final G3.F08). G3.F07 only ever persists AWAITING_PAYMENT
 * (CREATED is a transient state inside the sync orchestration chain, never stored).
 * The remaining states are produced by the back-half saga handled in F08.
 */
public enum OrderStatus {
    CREATED,
    AWAITING_PAYMENT,
    PAYMENT_FAILED,
    CARD_GENERATING,
    ON_DELIVERY,
    ORDER_SUCCESS,
    ORDER_FAILED,
    ON_COMPENSATED,
    ORDER_COMPLETED,
    SUSPENDED
}
