package com.zdc.order.outbox;

import com.zdc.order.domain.OrderOutboxEntity;
import com.zdc.order.domain.OutboxStatus;
import com.zdc.order.repository.OrderOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Drains the order_outbox to the saga topic (ADR-018). F07 emits only
 * ORDER_CREATED. Processes the oldest due batch; on publish failure it backs off
 * and retires the row to DEAD once max_attempts is exhausted.
 */
@Component
public class OrderOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderOutboxPublisher.class);
    private static final int BATCH_SIZE = 10;

    private final OrderOutboxRepository repository;
    private final EventPublisher eventPublisher;

    public OrderOutboxPublisher(OrderOutboxRepository repository, EventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    /** @return number of events published this cycle. */
    @Transactional
    public int publishPending() {
        List<OrderOutboxEntity> due = repository
                .findByStatusAndNextRetryAtLessThanEqualOrderByIdAsc(OutboxStatus.PENDING, Instant.now())
                .stream()
                .limit(BATCH_SIZE)
                .toList();

        int published = 0;
        for (OrderOutboxEntity event : due) {
            try {
                eventPublisher.publish(event);
                event.setStatus(OutboxStatus.DONE);
                published++;
            } catch (RuntimeException ex) {
                handleFailure(event, ex);
            }
            repository.save(event);
        }
        return published;
    }

    private void handleFailure(OrderOutboxEntity event, RuntimeException ex) {
        log.warn("Failed to publish outbox event {}: {}", event.getEventId(), ex.getMessage());
        event.setAttemptCount(event.getAttemptCount() + 1);
        event.setLastError(ex.getMessage());
        if (event.getAttemptCount() >= event.getMaxAttempts()) {
            event.setStatus(OutboxStatus.DEAD);
        } else {
            event.setStatus(OutboxStatus.PENDING);
            event.setNextRetryAt(Instant.now().plus(Duration.ofSeconds(2L * event.getAttemptCount())));
        }
    }
}
