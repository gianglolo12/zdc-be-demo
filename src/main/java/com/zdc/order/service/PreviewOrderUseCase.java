package com.zdc.order.service;

import com.zdc.order.error.ErrorCode;
import com.zdc.order.error.OrderException;
import com.zdc.order.gateway.ChannelView;
import com.zdc.order.gateway.CommercialGateway;
import com.zdc.order.gateway.PricingGateway;
import com.zdc.order.service.OrderResolutionService.ResolvedItem;
import com.zdc.order.service.OrderResolutionService.Resolution;
import com.zdc.order.support.CurrencyResolver;
import com.zdc.order.web.AgentContext;
import com.zdc.order.web.dto.AppliedDiscountDto;
import com.zdc.order.web.dto.ChannelPricingDto;
import com.zdc.order.web.dto.LineItemBreakdown;
import com.zdc.order.web.dto.PreviewOrderRequest;
import com.zdc.order.web.dto.PreviewOrderResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FS-003 — read-only preview. Resolves items + channels, asks Pricing for the
 * per-channel breakdown, checks credit, and flags {@code fits_amount} /
 * {@code exceed_credit_limit} without ever throwing on credit (BR-003). Nothing
 * is persisted (BR-006).
 */
@Service
public class PreviewOrderUseCase {

    private final OrderResolutionService resolution;
    private final PricingGateway pricingGateway;
    private final CommercialGateway commercialGateway;
    private final CurrencyResolver currencyResolver;

    public PreviewOrderUseCase(OrderResolutionService resolution,
                               PricingGateway pricingGateway,
                               CommercialGateway commercialGateway,
                               CurrencyResolver currencyResolver) {
        this.resolution = resolution;
        this.pricingGateway = pricingGateway;
        this.commercialGateway = commercialGateway;
        this.currencyResolver = currencyResolver;
    }

    public PreviewOrderResponse execute(AgentContext agent, PreviewOrderRequest request) {
        Resolution resolved = resolution.resolve(
                request == null ? null : request.items(),
                agent.country(),
                request == null ? null : request.paymentChannelId());

        List<PricingGateway.ChannelPricing> pricings = callPricing(agent, resolved);
        long availableCredit = currentAvailableCredit(agent.agentId());

        Map<Long, ChannelView> channelById = resolved.channels().stream()
                .collect(Collectors.toMap(ChannelView::id, Function.identity()));

        List<ChannelPricingDto> channels = new ArrayList<>();
        boolean anyFits = false;
        for (PricingGateway.ChannelPricing p : pricings) {
            ChannelView channel = channelById.get(p.channelId());
            if (channel == null) {
                continue;
            }
            boolean fits = p.grandTotal() >= channel.minAmount() && p.grandTotal() <= channel.maxAmount();
            anyFits = anyFits || fits;
            channels.add(toDto(channel, p, fits, p.grandTotal() > availableCredit));
        }

        if (!anyFits) {
            throw new OrderException(ErrorCode.ERR_QUANTITY_EXCEEDED);
        }

        PreviewOrderResponse.Data data = new PreviewOrderResponse.Data(
                resolved.subtotal(),
                currencyResolver.resolve(agent.country()),
                availableCredit,
                channels);
        return new PreviewOrderResponse(data);
    }

    private List<PricingGateway.ChannelPricing> callPricing(AgentContext agent, Resolution resolved) {
        List<PricingGateway.PricingRequest.Item> items = resolved.items().stream()
                .map(i -> new PricingGateway.PricingRequest.Item(
                        i.product().id(), i.product().unitPrice(), i.quantity()))
                .toList();
        List<PricingGateway.PricingRequest.Channel> channels = resolved.channels().stream()
                .map(c -> new PricingGateway.PricingRequest.Channel(
                        c.id(), c.paymentMethod(), c.paymentProvider(), c.minAmount(), c.maxAmount()))
                .toList();
        PricingGateway.PricingRequest req =
                new PricingGateway.PricingRequest(agent.agentId(), agent.country(), items, channels);
        try {
            return pricingGateway.calculate(req);
        } catch (OrderException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new OrderException(ErrorCode.ERR_INTERNAL);
        }
    }

    private long currentAvailableCredit(Long agentId) {
        try {
            CommercialGateway.CreditInfo info = commercialGateway.getAvailableCredit(agentId);
            return info.availableCredit() == null ? 0L : info.availableCredit();
        } catch (OrderException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new OrderException(ErrorCode.ERR_INTERNAL);
        }
    }

    private ChannelPricingDto toDto(ChannelView channel, PricingGateway.ChannelPricing p,
                                    boolean fits, boolean exceedCredit) {
        List<LineItemBreakdown> lines = p.lines().stream()
                .map(l -> new LineItemBreakdown(
                        l.productId(), l.quantity(), l.unitPriceOriginal(), l.unitPriceDiscounted(),
                        l.lineSubtotal(), l.lineDiscount(), l.lineTotal()))
                .toList();
        List<AppliedDiscountDto> discounts = p.appliedDiscounts().stream()
                .map(d -> new AppliedDiscountDto(
                        d.policyId(), d.policyName(), d.scope().name(), d.discountType().name(),
                        d.discountValue(), d.appliedAmount(), d.targetItemId()))
                .toList();
        return new ChannelPricingDto(
                channel.id(), channel.paymentMethod(), channel.paymentProvider(),
                fits, p.grandTotal(), p.itemDiscountTotal(), p.orderDiscount(),
                p.channelFee() == null ? 0L : p.channelFee(),
                exceedCredit, lines, discounts);
    }
}
