package com.zdc.order.domain;

/** Outbox row lifecycle (ADR-018 outbox pattern). */
public enum OutboxStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED,
    DEAD
}
