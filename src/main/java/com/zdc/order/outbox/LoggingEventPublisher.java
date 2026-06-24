package com.zdc.order.outbox;

import com.zdc.order.domain.OrderOutboxEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Default {@link EventPublisher}: logs the event. Replaced by the Kafka producer
 * in F08 (which will register its own bean, disabling this one).
 */
@Component
@ConditionalOnMissingBean(name = "kafkaEventPublisher")
public class LoggingEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);

    @Override
    public void publish(OrderOutboxEntity event) {
        log.info("Publishing saga event type={} eventId={} aggregateId={} payload={}",
                event.getEventType(), event.getEventId(), event.getAggregateId(), event.getPayload());
    }
}
