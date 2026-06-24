package com.zdc.order.repository;

import com.zdc.order.domain.OrderOutboxEntity;
import com.zdc.order.domain.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface OrderOutboxRepository extends JpaRepository<OrderOutboxEntity, Long> {

    /**
     * Poll due events oldest-first. The Order DB is single-writer for the
     * publisher in P1; FOR UPDATE SKIP LOCKED hardening is deferred (Q-T-2).
     */
    List<OrderOutboxEntity> findByStatusAndNextRetryAtLessThanEqualOrderByIdAsc(
            OutboxStatus status, Instant now);
}
