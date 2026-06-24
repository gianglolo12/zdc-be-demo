package com.zdc.order.service;

import com.zdc.order.config.OrderProperties;
import com.zdc.order.domain.OrderEntity;
import com.zdc.order.domain.OrderPaymentEntity;
import com.zdc.order.domain.OrderStatus;
import com.zdc.order.error.ErrorCode;
import com.zdc.order.error.OrderException;
import com.zdc.order.gateway.ChannelView;
import com.zdc.order.gateway.CommercialGateway;
import com.zdc.order.gateway.PaymentGateway;
import com.zdc.order.gateway.PricingGateway;
import com.zdc.order.gateway.ProductView;
import com.zdc.order.service.OrderResolutionService.ResolvedItem;
import com.zdc.order.service.OrderResolutionService.Resolution;
import com.zdc.order.support.CurrencyResolver;
import com.zdc.order.support.LockService;
import com.zdc.order.web.AgentContext;
import com.zdc.order.web.dto.CreateOrderRequest;
import com.zdc.order.web.dto.CreateOrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateOrderUseCaseTest {

    private final AgentContext agent = new AgentContext(555L, "ACTIVE", "vn");

    private LockService lockService;
    private OrderResolutionService resolution;
    private PricingGateway pricingGateway;
    private CommercialGateway commercialGateway;
    private PaymentGateway paymentGateway;
    private OrderPersistenceService persistenceService;
    private CreateOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        lockService = mock(LockService.class);
        resolution = mock(OrderResolutionService.class);
        pricingGateway = mock(PricingGateway.class);
        commercialGateway = mock(CommercialGateway.class);
        paymentGateway = mock(PaymentGateway.class);
        persistenceService = mock(OrderPersistenceService.class);
        useCase = new CreateOrderUseCase(lockService, resolution, pricingGateway, commercialGateway,
                paymentGateway, persistenceService, new CurrencyResolver(), new OrderProperties());
    }

    private CreateOrderRequest request() {
        return new CreateOrderRequest(List.of(new com.zdc.order.web.dto.OrderItemInput(2L, 100)), 1L);
    }

    private void stubResolution() {
        ProductView product = new ProductView(2L, "Thẻ Zing 50.000đ", "ZING", 50000L, 50000L, null, "vn", true);
        ChannelView channel = new ChannelView(1L, "VIET_QR", "ZALOPAY", 10000L, 50_000_000L, "vn", true);
        when(resolution.resolve(any(), eq("vn"), eq(1L))).thenReturn(new Resolution(
                List.of(new ResolvedItem(product, 100, 5_000_000L)), 5_000_000L, List.of(channel)));
    }

    private void stubPricing(long grandTotal) {
        when(pricingGateway.calculate(any())).thenReturn(List.of(new PricingGateway.ChannelPricing(
                1L, 5_000_000L, 250_000L, 0L, 0L, grandTotal, List.of(), List.of())));
    }

    private PaymentGateway.PaymentInitResult paymentResult() {
        return new PaymentGateway.PaymentInitResult(456L, "ESP_1", "https://esp/checkout",
                "<qr>", "zalopay://x", Instant.parse("2026-05-21T11:00:00Z"));
    }

    private void stubPersist() {
        OrderEntity order = new OrderEntity();
        order.setAgentId(555L);
        order.setCurrency("VND");
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        order.setSubtotal(5_000_000L);
        order.setGrandTotal(4_900_000L);
        OrderPaymentEntity payment = new OrderPaymentEntity();
        payment.setOrderId(789L);
        when(persistenceService.persist(any()))
                .thenReturn(new OrderPersistenceService.PersistResult(order, payment));
    }

    @Test
    void happyPathChargesSubmitsAndPersists() {
        when(lockService.acquire(anyString(), any())).thenReturn(true);
        stubResolution();
        stubPricing(4_900_000L);
        when(commercialGateway.charge(eq(555L), anyString(), eq(4_900_000L)))
                .thenReturn(new CommercialGateway.ChargeResult(true, 100_000L));
        when(paymentGateway.initAndSubmit(any())).thenReturn(paymentResult());
        stubPersist();

        CreateOrderResponse resp = useCase.execute(agent, request());

        assertThat(resp.data().status()).isEqualTo("AWAITING_PAYMENT");
        assertThat(resp.data().grandTotal()).isEqualTo(4_900_000L);
        assertThat(resp.data().paymentUrl()).isEqualTo("https://esp/checkout");
        assertThat(resp.data().expiredAt()).isEqualTo(Instant.parse("2026-05-21T11:00:00Z"));
        verify(persistenceService).persist(any());
        verify(lockService).release("order:agent:555");
    }

    @Test
    void concurrentLockRejected() {
        when(lockService.acquire(anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(agent, request()))
                .isInstanceOf(OrderException.class)
                .extracting(e -> ((OrderException) e).code())
                .isEqualTo(ErrorCode.ERR_CONCURRENT_OPERATION);
        verify(persistenceService, never()).persist(any());
    }

    @Test
    void missingChannelRejected() {
        when(lockService.acquire(anyString(), any())).thenReturn(true);
        CreateOrderRequest noChannel =
                new CreateOrderRequest(List.of(new com.zdc.order.web.dto.OrderItemInput(2L, 100)), null);

        assertThatThrownBy(() -> useCase.execute(agent, noChannel))
                .isInstanceOf(OrderException.class)
                .extracting(e -> ((OrderException) e).code())
                .isEqualTo(ErrorCode.ERR_INVALID_REQUEST);
    }

    @Test
    void creditInsufficientRollsBackWithoutPersist() {
        when(lockService.acquire(anyString(), any())).thenReturn(true);
        stubResolution();
        stubPricing(4_900_000L);
        when(commercialGateway.charge(eq(555L), anyString(), anyLong()))
                .thenReturn(new CommercialGateway.ChargeResult(false, 3_000_000L));

        OrderException ex = catchOrderException(() -> useCase.execute(agent, request()));
        assertThat(ex.code()).isEqualTo(ErrorCode.ERR_CREDIT_INSUFFICIENT);
        assertThat(ex.details()).containsEntry("available_credit", 3_000_000L);
        verify(persistenceService, never()).persist(any());
        verify(paymentGateway, never()).initAndSubmit(any());
    }

    @Test
    void creditExhaustedWhenZeroAvailable() {
        when(lockService.acquire(anyString(), any())).thenReturn(true);
        stubResolution();
        stubPricing(4_900_000L);
        when(commercialGateway.charge(eq(555L), anyString(), anyLong()))
                .thenReturn(new CommercialGateway.ChargeResult(false, 0L));

        assertThat(catchOrderException(() -> useCase.execute(agent, request())).code())
                .isEqualTo(ErrorCode.ERR_CREDIT_EXHAUSTED);
    }

    @Test
    void paymentFailureReleasesCreditAndDoesNotPersist() {
        when(lockService.acquire(anyString(), any())).thenReturn(true);
        stubResolution();
        stubPricing(4_900_000L);
        when(commercialGateway.charge(eq(555L), anyString(), anyLong()))
                .thenReturn(new CommercialGateway.ChargeResult(true, 100_000L));
        when(paymentGateway.initAndSubmit(any()))
                .thenThrow(new PaymentGateway.PaymentGatewayException("ESP down"));

        assertThat(catchOrderException(() -> useCase.execute(agent, request())).code())
                .isEqualTo(ErrorCode.ERR_PAYMENT_GATEWAY);
        verify(commercialGateway).release(eq(555L), anyString());
        verify(persistenceService, never()).persist(any());
    }

    @Test
    void dbFailureCompensatesAndReturns500() {
        when(lockService.acquire(anyString(), any())).thenReturn(true);
        stubResolution();
        stubPricing(4_900_000L);
        when(commercialGateway.charge(eq(555L), anyString(), anyLong()))
                .thenReturn(new CommercialGateway.ChargeResult(true, 100_000L));
        when(paymentGateway.initAndSubmit(any())).thenReturn(paymentResult());
        when(persistenceService.persist(any())).thenThrow(new RuntimeException("deadlock"));

        OrderException ex = catchOrderException(() -> useCase.execute(agent, request()));
        assertThat(ex.code()).isEqualTo(ErrorCode.ERR_INTERNAL);
        assertThat(ex.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        verify(commercialGateway).release(eq(555L), anyString());
    }

    @Test
    void amountExceedingChannelRejected() {
        when(lockService.acquire(anyString(), any())).thenReturn(true);
        ProductView product = new ProductView(2L, "Thẻ Zing", "ZING", 50000L, 50000L, null, "vn", true);
        ChannelView smallChannel = new ChannelView(1L, "VIET_QR", "ZALOPAY", 10000L, 1_000_000L, "vn", true);
        when(resolution.resolve(any(), eq("vn"), eq(1L))).thenReturn(new Resolution(
                List.of(new ResolvedItem(product, 100, 5_000_000L)), 5_000_000L, List.of(smallChannel)));
        stubPricing(4_900_000L);

        assertThat(catchOrderException(() -> useCase.execute(agent, request())).code())
                .isEqualTo(ErrorCode.ERR_QUANTITY_EXCEEDED);
        verify(commercialGateway, never()).charge(anyLong(), anyString(), anyLong());
    }

    private static OrderException catchOrderException(Runnable r) {
        try {
            r.run();
        } catch (OrderException ex) {
            return ex;
        }
        throw new AssertionError("Expected OrderException");
    }
}
