package com.zdc.order.outbox;

import com.zdc.order.domain.OrderOutboxEntity;

/**
 * Port for publishing outbox events to the saga topic {@code zdc.order.saga.events}.
 * The Kafka adapter is wired in F08; P1 ships a logging implementation so the
 * outbox drains without requiring a broker in this module.
 */
public interface EventPublisher {

    void publish(OrderOutboxEntity event);
}
