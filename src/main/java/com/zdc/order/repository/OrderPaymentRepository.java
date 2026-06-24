package com.zdc.order.repository;

import com.zdc.order.domain.OrderPaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderPaymentRepository extends JpaRepository<OrderPaymentEntity, Long> {
    List<OrderPaymentEntity> findByOrderId(Long orderId);
}
