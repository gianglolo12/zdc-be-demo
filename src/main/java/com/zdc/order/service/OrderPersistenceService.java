package com.zdc.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdc.order.domain.OrderDetailEntity;
import com.zdc.order.domain.OrderDiscountEntity;
import com.zdc.order.domain.OrderEntity;
import com.zdc.order.domain.OrderOutboxEntity;
import com.zdc.order.domain.OrderPaymentEntity;
import com.zdc.order.domain.OrderStatus;
import com.zdc.order.domain.OutboxStatus;
import com.zdc.order.domain.PaymentStatus;
import com.zdc.order.gateway.PaymentGateway;
import com.zdc.order.gateway.PricingGateway;
import com.zdc.order.repository.OrderDetailRepository;
import com.zdc.order.repository.OrderDiscountRepository;
import com.zdc.order.repository.OrderOutboxRepository;
import com.zdc.order.repository.OrderPaymentRepository;
import com.zdc.order.repository.OrderRepository;
import com.zdc.order.service.OrderResolutionService.ResolvedItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FS-004 step 8 — atomically persists the order aggregate (order, order_detail,
 * order_discounts, order_payment) and enqueues the ORDER_CREATED outbox event.
 * One {@code @Transactional} boundary at the service layer (rule-layering): the
 * remote orchestration (charge/submit) already happened in the caller.
 */
@Service
public class OrderPersistenceService {

    public static final String EVENT_ORDER_CREATED = "ORDER_CREATED";

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final OrderDiscountRepository orderDiscountRepository;
    private final OrderPaymentRepository orderPaymentRepository;
    private final OrderOutboxRepository orderOutboxRepository;
    private final ObjectMapper objectMapper;

    public OrderPersistenceService(OrderRepository orderRepository,
                                   OrderDetailRepository orderDetailRepository,
                                   OrderDiscountRepository orderDiscountRepository,
                                   OrderPaymentRepository orderPaymentRepository,
                                   OrderOutboxRepository orderOutboxRepository,
                                   ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.orderDetailRepository = orderDetailRepository;
        this.orderDiscountRepository = orderDiscountRepository;
        this.orderPaymentRepository = orderPaymentRepository;
        this.orderOutboxRepository = orderOutboxRepository;
        this.objectMapper = objectMapper;
    }

    public record PersistCommand(
            Long agentId,
            String currency,
            long subtotal,
            List<ResolvedItem> items,
            PricingGateway.ChannelPricing pricing,
            Long channelId,
            PaymentGateway.PaymentInitResult payment) {
    }

    public record PersistResult(OrderEntity order, OrderPaymentEntity payment) {
    }

    @Transactional
    public PersistResult persist(PersistCommand cmd) {
        OrderEntity order = new OrderEntity();
        order.setAgentId(cmd.agentId());
        order.setCurrency(cmd.currency());
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        order.setSubtotal(cmd.subtotal());
        order.setOrderDiscount(cmd.pricing().orderDiscount() == null ? 0L : cmd.pricing().orderDiscount());
        order.setGrandTotal(cmd.pricing().grandTotal());
        order.setExpiredAt(cmd.payment().expiredAt());
        order = orderRepository.save(order);
        Long orderId = order.getId();

        persistDetails(orderId, cmd);
        persistDiscounts(orderId, cmd.pricing());
        OrderPaymentEntity payment = persistPayment(orderId, cmd);
        enqueueOrderCreated(order, cmd);

        return new PersistResult(order, payment);
    }

    private void persistDetails(Long orderId, PersistCommand cmd) {
        Map<Long, PricingGateway.ChannelPricing.Line> linesByProduct = new LinkedHashMap<>();
        for (PricingGateway.ChannelPricing.Line line : cmd.pricing().lines()) {
            linesByProduct.put(line.productId(), line);
        }
        for (ResolvedItem item : cmd.items()) {
            PricingGateway.ChannelPricing.Line line = linesByProduct.get(item.product().id());
            OrderDetailEntity detail = new OrderDetailEntity();
            detail.setOrderId(orderId);
            detail.setProductId(item.product().id());
            detail.setQuantity(item.quantity());
            detail.setUnitPriceOriginal(item.product().unitPrice());
            if (line != null) {
                detail.setUnitPriceDiscounted(line.unitPriceDiscounted());
                detail.setLineSubtotal(line.lineSubtotal());
                detail.setLineDiscount(line.lineDiscount() == null ? 0L : line.lineDiscount());
                detail.setLineTotal(line.lineTotal());
            } else {
                detail.setUnitPriceDiscounted(item.product().unitPrice());
                detail.setLineSubtotal(item.lineSubtotal());
                detail.setLineDiscount(0L);
                detail.setLineTotal(item.lineSubtotal());
            }
            orderDetailRepository.save(detail);
        }
    }

    private void persistDiscounts(Long orderId, PricingGateway.ChannelPricing pricing) {
        if (pricing.appliedDiscounts() == null) {
            return;
        }
        for (PricingGateway.ChannelPricing.AppliedDiscount d : pricing.appliedDiscounts()) {
            OrderDiscountEntity entity = new OrderDiscountEntity();
            entity.setOrderId(orderId);
            entity.setPolicyId(d.policyId());
            entity.setPolicyName(d.policyName());
            entity.setScope(d.scope());
            entity.setTargetItemId(d.targetItemId());
            entity.setDiscountType(d.discountType());
            entity.setDiscountValue(d.discountValue());
            entity.setDiscountAmount(d.appliedAmount());
            orderDiscountRepository.save(entity);
        }
    }

    private OrderPaymentEntity persistPayment(Long orderId, PersistCommand cmd) {
        OrderPaymentEntity payment = new OrderPaymentEntity();
        payment.setOrderId(orderId);
        payment.setPaymentTransId(cmd.payment().paymentTransId());
        payment.setChannelId(cmd.channelId());
        payment.setStatus(PaymentStatus.CREATED);
        payment.setAttemptNumber(1);
        return orderPaymentRepository.save(payment);
    }

    private void enqueueOrderCreated(OrderEntity order, PersistCommand cmd) {
        OrderOutboxEntity outbox = new OrderOutboxEntity();
        outbox.setEventId(UUID.randomUUID().toString());
        outbox.setEventType(EVENT_ORDER_CREATED);
        outbox.setAggregateId(order.getId());
        outbox.setPayload(buildPayload(order, cmd));
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setNextRetryAt(Instant.now());
        orderOutboxRepository.save(outbox);
    }

    private String buildPayload(OrderEntity order, PersistCommand cmd) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ResolvedItem item : cmd.items()) {
            Map<String, Object> i = new LinkedHashMap<>();
            i.put("product_id", item.product().id());
            i.put("quantity", item.quantity());
            items.add(i);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("order_id", order.getId());
        payload.put("agent_id", order.getAgentId());
        payload.put("grand_total", order.getGrandTotal());
        payload.put("currency", order.getCurrency());
        payload.put("channel_id", cmd.channelId());
        payload.put("items", items);
        payload.put("attempt_number", 1);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize ORDER_CREATED payload", ex);
        }
    }
}
