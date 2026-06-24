package com.zdc.order.service;

import com.zdc.order.domain.DiscountScope;
import com.zdc.order.domain.DiscountType;
import com.zdc.order.error.ErrorCode;
import com.zdc.order.error.OrderException;
import com.zdc.order.gateway.ChannelView;
import com.zdc.order.gateway.CommercialGateway;
import com.zdc.order.gateway.PaymentChannelGateway;
import com.zdc.order.gateway.PricingGateway;
import com.zdc.order.gateway.ProductGateway;
import com.zdc.order.gateway.ProductView;
import com.zdc.order.support.CurrencyResolver;
import com.zdc.order.web.AgentContext;
import com.zdc.order.web.dto.OrderItemInput;
import com.zdc.order.web.dto.PreviewOrderRequest;
import com.zdc.order.web.dto.PreviewOrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreviewOrderUseCaseTest {

    private final AgentContext agent = new AgentContext(555L, "ACTIVE", "vn");

    private ProductGateway productGateway;
    private PaymentChannelGateway channelGateway;
    private PricingGateway pricingGateway;
    private CommercialGateway commercialGateway;
    private PreviewOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        productGateway = mock(ProductGateway.class);
        channelGateway = mock(PaymentChannelGateway.class);
        pricingGateway = mock(PricingGateway.class);
        commercialGateway = mock(CommercialGateway.class);
        OrderResolutionService resolution = new OrderResolutionService(
                new ProductCatalogService(productGateway),
                new PaymentChannelService(channelGateway),
                channelGateway);
        useCase = new PreviewOrderUseCase(resolution, pricingGateway, commercialGateway, new CurrencyResolver());
    }

    private static ProductView product50k() {
        return new ProductView(2L, "Thẻ Zing 50.000đ", "ZING", 50000L, 50000L, null, "vn", true);
    }

    private static ChannelView channel(long max) {
        return new ChannelView(1L, "VIET_QR", "ZALOPAY", 10000L, max, "vn", true);
    }

    private PricingGateway.ChannelPricing pricing(long grandTotal) {
        return new PricingGateway.ChannelPricing(
                1L, 5_000_000L, 250_000L, 0L, 0L, grandTotal,
                List.of(new PricingGateway.ChannelPricing.Line(2L, 100, 50000L, 47500L, 5_000_000L, 250_000L, 4_750_000L)),
                List.of(new PricingGateway.ChannelPricing.AppliedDiscount(
                        1L, "Mass 5% Zing 50K", DiscountScope.ITEM, DiscountType.PERCENTAGE,
                        new BigDecimal("5"), 250_000L, 2L)));
    }

    private void stubHappyCatalog(long channelMax) {
        when(productGateway.findByCountry("vn")).thenReturn(List.of(product50k()));
        when(channelGateway.findChannelIdsForProduct(2L)).thenReturn(Set.of(1L));
        when(channelGateway.findByCountry("vn")).thenReturn(List.of(channel(channelMax)));
    }

    @Test
    void previewFlagsExceedCreditButDoesNotThrow() {
        stubHappyCatalog(50_000_000L);
        when(pricingGateway.calculate(any())).thenReturn(List.of(pricing(4_750_000L)));
        when(commercialGateway.getAvailableCredit(555L))
                .thenReturn(new CommercialGateway.CreditInfo(789L, 555L, 5_000_000L, 3_000_000L));

        PreviewOrderResponse resp = useCase.execute(agent,
                new PreviewOrderRequest(List.of(new OrderItemInput(2L, 100)), 1L));

        assertThat(resp.data().subtotal()).isEqualTo(5_000_000L);
        assertThat(resp.data().currency()).isEqualTo("VND");
        assertThat(resp.data().availableCredit()).isEqualTo(3_000_000L);
        assertThat(resp.data().availableChannels()).hasSize(1);
        var ch = resp.data().availableChannels().get(0);
        assertThat(ch.fitsAmount()).isTrue();
        assertThat(ch.exceedCreditLimit()).isTrue();
        assertThat(ch.grandTotal()).isEqualTo(4_750_000L);
        assertThat(ch.appliedDiscounts()).hasSize(1);
        assertThat(ch.lines()).hasSize(1);
    }

    @Test
    void emptyItemsRejected() {
        assertThatThrownBy(() -> useCase.execute(agent, new PreviewOrderRequest(List.of(), 1L)))
                .isInstanceOf(OrderException.class)
                .extracting(e -> ((OrderException) e).code())
                .isEqualTo(ErrorCode.ERR_INVALID_QUANTITY);
    }

    @Test
    void nonPositiveQuantityRejected() {
        assertThatThrownBy(() -> useCase.execute(agent,
                new PreviewOrderRequest(List.of(new OrderItemInput(2L, 0)), 1L)))
                .isInstanceOf(OrderException.class)
                .extracting(e -> ((OrderException) e).code())
                .isEqualTo(ErrorCode.ERR_INVALID_QUANTITY);
    }

    @Test
    void unknownProductRejected() {
        when(productGateway.findByCountry("vn")).thenReturn(List.of());
        assertThatThrownBy(() -> useCase.execute(agent,
                new PreviewOrderRequest(List.of(new OrderItemInput(2L, 100)), 1L)))
                .isInstanceOf(OrderException.class)
                .extracting(e -> ((OrderException) e).code())
                .isEqualTo(ErrorCode.ERR_PRODUCT_UNAVAILABLE);
    }

    @Test
    void amountExceedingAllChannelsRejected() {
        stubHappyCatalog(1_000_000L); // max below grand_total
        when(pricingGateway.calculate(any())).thenReturn(List.of(pricing(4_750_000L)));
        when(commercialGateway.getAvailableCredit(555L))
                .thenReturn(new CommercialGateway.CreditInfo(789L, 555L, 5_000_000L, 3_000_000L));

        assertThatThrownBy(() -> useCase.execute(agent,
                new PreviewOrderRequest(List.of(new OrderItemInput(2L, 100)), 1L)))
                .isInstanceOf(OrderException.class)
                .extracting(e -> ((OrderException) e).code())
                .isEqualTo(ErrorCode.ERR_QUANTITY_EXCEEDED);
    }

    @Test
    void commercialDownMapsToInternal() {
        stubHappyCatalog(50_000_000L);
        when(pricingGateway.calculate(any())).thenReturn(List.of(pricing(4_750_000L)));
        when(commercialGateway.getAvailableCredit(eq(555L))).thenThrow(new RuntimeException("conn refused"));

        assertThatThrownBy(() -> useCase.execute(agent,
                new PreviewOrderRequest(List.of(new OrderItemInput(2L, 100)), 1L)))
                .isInstanceOf(OrderException.class)
                .extracting(e -> ((OrderException) e).code())
                .isEqualTo(ErrorCode.ERR_INTERNAL);
    }
}
