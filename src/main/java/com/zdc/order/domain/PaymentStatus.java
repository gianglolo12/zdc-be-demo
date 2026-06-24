package com.zdc.order.domain;

/** Simplified mirror of Payment Service transaction.status (FRS order_payment). */
public enum PaymentStatus {
    INIT,
    CREATED,
    SUCCESS,
    FAILED
}
