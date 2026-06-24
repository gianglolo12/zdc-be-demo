package com.zdc.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/** One snapshotted order line (FRS order_detail). */
@Entity
@Table(name = "order_detail")
public class OrderDetailEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price_original", nullable = false)
    private Long unitPriceOriginal;

    @Column(name = "unit_price_discounted", nullable = false)
    private Long unitPriceDiscounted;

    @Column(name = "line_subtotal", nullable = false)
    private Long lineSubtotal;

    @Column(name = "line_discount", nullable = false)
    private Long lineDiscount;

    @Column(name = "line_total", nullable = false)
    private Long lineTotal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (lineDiscount == null) {
            lineDiscount = 0L;
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

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Long getUnitPriceOriginal() {
        return unitPriceOriginal;
    }

    public void setUnitPriceOriginal(Long unitPriceOriginal) {
        this.unitPriceOriginal = unitPriceOriginal;
    }

    public Long getUnitPriceDiscounted() {
        return unitPriceDiscounted;
    }

    public void setUnitPriceDiscounted(Long unitPriceDiscounted) {
        this.unitPriceDiscounted = unitPriceDiscounted;
    }

    public Long getLineSubtotal() {
        return lineSubtotal;
    }

    public void setLineSubtotal(Long lineSubtotal) {
        this.lineSubtotal = lineSubtotal;
    }

    public Long getLineDiscount() {
        return lineDiscount;
    }

    public void setLineDiscount(Long lineDiscount) {
        this.lineDiscount = lineDiscount;
    }

    public Long getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(Long lineTotal) {
        this.lineTotal = lineTotal;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
