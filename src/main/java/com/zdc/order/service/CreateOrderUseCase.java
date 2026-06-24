package com.zdc.order.service;

import com.zdc.order.config.OrderProperties;
import com.zdc.order.domain.OrderEntity;
import com.zdc.order.domain.OrderPaymentEntity;
import com.zdc.order.error.ErrorCode;
import com.zdc.order.error.OrderException;
import com.zdc.order.gateway.ChannelView;
import com.zdc.order.gateway.CommercialGateway;
import com.zdc.order.gateway.PaymentGateway;
import com.zdc.order.gateway.PricingGateway;
import com.zdc.order.service.OrderPersistenceService.PersistCommand;
import com.zdc.order.service.OrderPersistenceService.PersistResult;
import com.zdc.order.service.OrderResolutionService.Resolution;
import com.zdc.order.support.CurrencyResolver;
import com.zdc.order.web.AgentContext;
import com.zdc.order.web.dto.CreateOrderRequest;
import com.zdc.order.web.dto.CreateOrderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FS-004 — sync orchestration for order creation (B2-final). Acquires the
 * anti double-click lock, validates + prices the single chosen channel, charges
 * Commercial atomically, submits to Payment/ESP, then persists the order
 * aggregate atomically and returns the PSP redirect payload. Any downstream
 * failure compensates (release credit) and persists nothing.
 */
@Service
public class CreateOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateOrderUseCase.class);
    private static final String LOCK_PREFIX = "order:agent:";

    private final com.zdc.order.support.LockService lockService;
    private final OrderResolutionService resolution;
    private final PricingGateway pricingGateway;
    private final CommercialGateway commercialGateway;
    private final PaymentGateway paymentGateway;
    private final OrderPersistenceService persistenceService;
    private final CurrencyResolver currencyResolver;
    private final OrderProperties properties;

    public CreateOrderUseCase(com.zdc.order.support.LockService lockService,
                              OrderResolutionService resolution,
                              PricingGateway pricingGateway,
                              CommercialGateway commercialGateway,
                              PaymentGateway paymentGateway,
                              OrderPersistenceService persistenceService,
                              CurrencyResolver currencyResolver,
                              OrderProperties properties) {
        this.lockService = lockService;
        this.resolution = resolution;
        this.pricingGateway = pricingGateway;
        this.commercialGateway = commercialGateway;
        this.paymentGateway = paymentGateway;
        this.persistenceService = persistenceService;
        this.currencyResolver = currencyResolver;
        this.properties = properties;
    }

    public CreateOrderResponse execute(AgentContext agent, CreateOrderRequest request) {
        if (request == null || request.paymentChannelId() == null) {
            throw new OrderException(ErrorCode.ERR_INVALID_REQUEST);
        }
        String lockKey = LOCK_PREFIX + agent.agentId();
        if (!lockService.acquire(lockKey, properties.getCreateLockTtl())) {
            throw new OrderException(ErrorCode.ERR_CONCURRENT_OPERATION);
        }
        try {
            return doCreate(agent, request);
        } finally {
            lockService.release(lockKey);
        }
    }

    private CreateOrderResponse doCreate(AgentContext agent, CreateOrderRequest request) {
        Resolution resolved = resolution.resolve(request.items(), agent.country(), request.paymentChannelId());
        ChannelView channel = resolved.channels().get(0);
        String currency = currencyResolver.resolve(agent.country());
        String correlationId = UUID.randomUUID().toString();

        PricingGateway.ChannelPricing pricing = callPricing(agent, resolved, channel);
        long grandTotal = pricing.grandTotal();

        // Re-validate channel min/max against the actual amount (BR-001 re-validate).
        if (grandTotal < channel.minAmount() || grandTotal > channel.maxAmount()) {
            throw new OrderException(ErrorCode.ERR_QUANTITY_EXCEEDED);
        }

        charge(agent.agentId(), correlationId, grandTotal);

        PaymentGateway.PaymentInitResult payment;
        try {
            payment = paymentGateway.initAndSubmit(new PaymentGateway.PaymentInitRequest(
                    agent.agentId(), correlationId, channel.id(),
                    channel.paymentMethod(), channel.paymentProvider(),
                    grandTotal, currency, properties.getPaymentReturnUrl()));
        } catch (PaymentGateway.PaymentGatewayException ex) {
            releaseQuietly(agent.agentId(), correlationId);
            throw new OrderException(ErrorCode.ERR_PAYMENT_GATEWAY);
        } catch (RuntimeException ex) {
            releaseQuietly(agent.agentId(), correlationId);
            throw new OrderException(ErrorCode.ERR_INTERNAL);
        }

        PersistResult result;
        try {
            result = persistenceService.persist(new PersistCommand(
                    agent.agentId(), currency, resolved.subtotal(), resolved.items(),
                    pricing, channel.id(), payment));
        } catch (RuntimeException ex) {
            log.error("Order persist failed after charge+submit, compensating credit", ex);
            releaseQuietly(agent.agentId(), correlationId);
            throw new OrderException(ErrorCode.ERR_INTERNAL, ErrorCode.ERR_INTERNAL.message(),
                    null, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return buildResponse(result, payment, currency);
    }

    private PricingGateway.ChannelPricing callPricing(AgentContext agent, Resolution resolved, ChannelView channel) {
        List<PricingGateway.PricingRequest.Item> items = resolved.items().stream()
                .map(i -> new PricingGateway.PricingRequest.Item(
                        i.product().id(), i.product().unitPrice(), i.quantity()))
                .toList();
        PricingGateway.PricingRequest req = new PricingGateway.PricingRequest(
                agent.agentId(), agent.country(), items,
                List.of(new PricingGateway.PricingRequest.Channel(
                        channel.id(), channel.paymentMethod(), channel.paymentProvider(),
                        channel.minAmount(), channel.maxAmount())));
        try {
            List<PricingGateway.ChannelPricing> result = pricingGateway.calculate(req);
            return result.stream()
                    .filter(p -> channel.id().equals(p.channelId()))
                    .findFirst()
                    .orElseThrow(() -> new OrderException(ErrorCode.ERR_INTERNAL));
        } catch (OrderException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new OrderException(ErrorCode.ERR_INTERNAL);
        }
    }

    private void charge(Long agentId, String correlationId, long amount) {
        CommercialGateway.ChargeResult charge;
        try {
            charge = commercialGateway.charge(agentId, correlationId, amount);
        } catch (OrderException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new OrderException(ErrorCode.ERR_INTERNAL);
        }
        if (charge.charged()) {
            return;
        }
        long available = charge.availableCredit() == null ? 0L : charge.availableCredit();
        if (available <= 0) {
            throw new OrderException(ErrorCode.ERR_CREDIT_EXHAUSTED);
        }
        throw new OrderException(ErrorCode.ERR_CREDIT_INSUFFICIENT,
                ErrorCode.ERR_CREDIT_INSUFFICIENT.message(),
                Map.of("available_credit", available));
    }

    private void releaseQuietly(Long agentId, String correlationId) {
        try {
            commercialGateway.release(agentId, correlationId);
        } catch (RuntimeException ex) {
            log.error("Compensating credit release failed for agent {} correlation {}",
                    agentId, correlationId, ex);
        }
    }

    private CreateOrderResponse buildResponse(PersistResult result,
                                              PaymentGateway.PaymentInitResult payment,
                                              String currency) {
        OrderEntity order = result.order();
        OrderPaymentEntity orderPayment = result.payment();
        CreateOrderResponse.Data data = new CreateOrderResponse.Data(
                order.getId(),
                order.getStatus().name(),
                order.getSubtotal(),
                order.getGrandTotal(),
                currency,
                payment.paymentUrl(),
                payment.qrCode(),
                payment.deeplink(),
                payment.expiredAt(),
                orderPayment.getId(),
                order.getCreatedAt());
        return new CreateOrderResponse(data);
    }
}
