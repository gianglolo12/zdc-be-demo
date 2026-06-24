package com.zdc.order.outbox;

import com.zdc.order.domain.OrderOutboxEntity;
import com.zdc.order.domain.OutboxStatus;
import com.zdc.order.repository.OrderOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
class OrderOutboxPublisherTest {

    @MockBean EventPublisher eventPublisher;

    @Autowired OrderOutboxPublisher publisher;
    @Autowired OrderOutboxRepository repository;

    private OrderOutboxEntity newPending(String eventId, int maxAttempts) {
        OrderOutboxEntity e = new OrderOutboxEntity();
        e.setEventId(eventId);
        e.setEventType("ORDER_CREATED");
        e.setAggregateId(1L);
        e.setPayload("{\"order_id\":1}");
        e.setMaxAttempts(maxAttempts);
        return repository.save(e);
    }

    @Test
    void publishesPendingAndMarksDone() {
        doNothing().when(eventPublisher).publish(any());
        OrderOutboxEntity row = newPending("evt-done", 5);

        int published = publisher.publishPending();

        assertThat(published).isEqualTo(1);
        assertThat(repository.findById(row.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.DONE);
    }

    @Test
    void failureKeepsPendingAndIncrementsAttempts() {
        doThrow(new RuntimeException("broker down")).when(eventPublisher).publish(any());
        OrderOutboxEntity row = newPending("evt-retry", 5);

        publisher.publishPending();

        OrderOutboxEntity reloaded = repository.findById(row.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(reloaded.getAttemptCount()).isEqualTo(1);
        assertThat(reloaded.getLastError()).isEqualTo("broker down");
    }

    @Test
    void retiresToDeadWhenMaxAttemptsExhausted() {
        doThrow(new RuntimeException("broker down")).when(eventPublisher).publish(any());
        OrderOutboxEntity row = newPending("evt-dead", 1);

        publisher.publishPending();

        assertThat(repository.findById(row.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.DEAD);
    }
}
