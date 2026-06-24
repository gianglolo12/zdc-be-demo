package com.zdc.order.outbox;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the outbox every 1s (NFR: saga delay ≤ 1s). Disabled under the test
 * profile so tests drive {@link OrderOutboxPublisher#publishPending()} directly.
 */
@Component
@Profile("!test")
public class OutboxScheduler {

    private final OrderOutboxPublisher publisher;

    public OutboxScheduler(OrderOutboxPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${zdc.order.outbox-poll-ms:1000}")
    public void poll() {
        publisher.publishPending();
    }
}
