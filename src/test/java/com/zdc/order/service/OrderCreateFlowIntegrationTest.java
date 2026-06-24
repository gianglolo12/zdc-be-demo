package com.zdc.order.service;

import com.zdc.order.domain.DiscountScope;
import com.zdc.order.domain.DiscountType;
import com.zdc.order.domain.OrderDetailEntity;
import com.zdc.order.domain.OrderDiscountEntity;
import com.zdc.order.domain.OrderEntity;
import com.zdc.order.domain.OrderOutboxEntity;
import com.zdc.order.domain.OrderPaymentEntity;
import com.zdc.order.domain.OrderStatus;
import com.zdc.order.domain.OutboxStatus;
import com.zdc.order.domain.PaymentStatus;
import com.zdc.order.gateway.ChannelView;
import com.zdc.order.gateway.CommercialGateway;
import com.zdc.order.gateway.PaymentChannelGateway;
import com.zdc.order.gateway.PaymentGateway;
import com.zdc.order.gateway.PricingGateway;
import com.zdc.order.gateway.ProductGateway;
import com.zdc.order.gateway.ProductView;
import com.zdc.order.outbox.OrderOutboxPublisher;
import com.zdc.order.repository.OrderDetailRepository;
import com.zdc.order.repository.OrderDiscountRepository;
import com.zdc.order.repository.OrderOutboxRepository;
import com.zdc.order.repository.OrderPaymentRepository;
import com.zdc.order.repository.OrderRepository;
import com.zdc.order.web.AgentContext;
import com.zdc.order.web.dto.CreateOrderRequest;
import com.zdc.order.web.dto.CreateOrderResponse;
import com.zdc.order.web.dto.OrderItemInput;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** End-to-end persistence of the create flow on H2 with downstream services mocked. */
@SpringBootTest
@ActiveProfiles("test")
class OrderCreateFlowIntegrationTest {

    @MockBean ProductGateway productGateway;
    @MockBean PaymentChannelGateway channelGateway;
    @MockBean PricingGateway pricingGateway;
    @MockBean CommercialGateway commercialGateway;
    @MockBean PaymentGateway paymentGateway;

    @Autowired CreateOrderUseCase createOrderUseCase;
    @Autowired OrderOutboxPublisher outboxPublisher;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderDetailRepository orderDetailRepository;
    @Autowired OrderDiscountRepository orderDiscountRepository;
    @Autowired OrderPaymentRepository orderPaymentRepository;
    @Autowired OrderOutboxRepository orderOutboxRepository;

    private void stubDownstream() {
        when(productGateway.findByCountry("vn")).thenReturn(List.of(
                new ProductView(2L, "Thẻ Zing 50.000đ", "ZING", 50000L, 50000L, null, "vn", true)));
        when(channelGateway.findChannelIdsForProduct(2L)).thenReturn(Set.of(1L));
        when(channelGateway.findByCountry("vn")).thenReturn(List.of(
                new ChannelView(1L, "VIET_QR", "ZALOPAY", 10000L, 50_000_000L, "vn", true)));
        when(pricingGateway.calculate(any())).thenReturn(List.of(new PricingGateway.ChannelPricing(
                1L, 5_000_000L, 250_000L, 0L, 0L, 4_750_000L,
                List.of(new PricingGateway.ChannelPricing.Line(2L, 100, 50000L, 47500L, 5_000_000L, 250_000L, 4_750_000L)),
                List.of(new PricingGateway.ChannelPricing.AppliedDiscount(
                        1L, "Mass 5% Zing 50K", DiscountScope.ITEM, DiscountType.PERCENTAGE,
                        new BigDecimal("5"), 250_000L, 2L)))));
        when(commercialGateway.charge(eq(555L), anyString(), eq(4_750_000L)))
                .thenReturn(new CommercialGateway.ChargeResult(true, 250_000L));
        when(paymentGateway.initAndSubmit(any())).thenReturn(new PaymentGateway.PaymentInitResult(
                456L, "ESP_1", "https://esp/checkout", "<qr>", "zalopay://x",
                Instant.parse("2026-05-21T11:00:00Z")));
    }

    @Test
    void createPersistsAggregateAndEnqueuesOutbox() {
        stubDownstream();
        AgentContext agent = new AgentContext(555L, "ACTIVE", "vn");

        CreateOrderResponse resp = createOrderUseCase.execute(agent,
                new CreateOrderRequest(List.of(new OrderItemInput(2L, 100)), 1L));

        Long orderId = resp.data().orderId();
        assertThat(orderId).isNotNull();
        assertThat(resp.data().status()).isEqualTo("AWAITING_PAYMENT");
        assertThat(resp.data().referenceId()).isNotNull();
        assertThat(resp.data().paymentUrl()).isEqualTo("https://esp/checkout");

        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(order.getSubtotal()).isEqualTo(5_000_000L);
        assertThat(order.getGrandTotal()).isEqualTo(4_750_000L);
        assertThat(order.getCurrency()).isEqualTo("VND");
        assertThat(order.getExpiredAt()).isEqualTo(Instant.parse("2026-05-21T11:00:00Z"));

        List<OrderDetailEntity> details = orderDetailRepository.findByOrderId(orderId);
        assertThat(details).hasSize(1);
        assertThat(details.get(0).getLineTotal()).isEqualTo(4_750_000L);
        assertThat(details.get(0).getUnitPriceDiscounted()).isEqualTo(47500L);

        List<OrderDiscountEntity> discounts = orderDiscountRepository.findByOrderId(orderId);
        assertThat(discounts).hasSize(1);
        assertThat(discounts.get(0).getScope()).isEqualTo(DiscountScope.ITEM);
        assertThat(discounts.get(0).getDiscountAmount()).isEqualTo(250_000L);

        List<OrderPaymentEntity> payments = orderPaymentRepository.findByOrderId(orderId);
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(payments.get(0).getPaymentTransId()).isEqualTo(456L);
        assertThat(payments.get(0).getChannelId()).isEqualTo(1L);

        List<OrderOutboxEntity> outbox = orderOutboxRepository.findAll();
        assertThat(outbox).hasSize(1);
        assertThat(outbox.get(0).getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(outbox.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.get(0).getPayload()).contains("\"order_id\":" + orderId);

        // Outbox drains and the row flips to DONE.
        int published = outboxPublisher.publishPending();
        assertThat(published).isEqualTo(1);
        assertThat(orderOutboxRepository.findById(outbox.get(0).getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.DONE);
    }
}
