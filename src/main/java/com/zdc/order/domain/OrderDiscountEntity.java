package com.zdc.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Snapshot of a discount applied to an order (FRS order_discounts). */
@Entity
@Table(name = "order_discounts")
public class OrderDiscountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "policy_name", nullable = false, length = 200)
    private String policyName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DiscountScope scope;

    @Column(name = "target_item_id")
    private Long targetItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 16)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 15, scale = 4)
    private BigDecimal discountValue;

    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getPolicyId() {
        return policyId;
    }

    public void setPolicyId(Long policyId) {
        this.policyId = policyId;
    }

    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    public DiscountScope getScope() {
        return scope;
    }

    public void setScope(DiscountScope scope) {
        this.scope = scope;
    }

    public Long getTargetItemId() {
        return targetItemId;
    }

    public void setTargetItemId(Long targetItemId) {
        this.targetItemId = targetItemId;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public void setDiscountType(DiscountType discountType) {
        this.discountType = discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    public Long getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(Long discountAmount) {
        this.discountAmount = discountAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
