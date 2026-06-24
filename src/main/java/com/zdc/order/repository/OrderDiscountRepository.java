package com.zdc.order.repository;

import com.zdc.order.domain.OrderDiscountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderDiscountRepository extends JpaRepository<OrderDiscountEntity, Long> {
    List<OrderDiscountEntity> findByOrderId(Long orderId);
}
