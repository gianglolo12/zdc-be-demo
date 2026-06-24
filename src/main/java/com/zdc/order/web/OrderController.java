package com.zdc.order.web;

import com.zdc.order.gateway.ChannelView;
import com.zdc.order.gateway.ProductView;
import com.zdc.order.service.CreateOrderUseCase;
import com.zdc.order.service.PaymentChannelService;
import com.zdc.order.service.PreviewOrderUseCase;
import com.zdc.order.service.ProductCatalogService;
import com.zdc.order.web.dto.CreateOrderRequest;
import com.zdc.order.web.dto.CreateOrderResponse;
import com.zdc.order.web.dto.PaymentChannelDto;
import com.zdc.order.web.dto.PaymentChannelsResponse;
import com.zdc.order.web.dto.PreviewOrderRequest;
import com.zdc.order.web.dto.PreviewOrderResponse;
import com.zdc.order.web.dto.ProductDto;
import com.zdc.order.web.dto.ProductsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Order Service FE-facing endpoints (api-contract G3-F07). Controllers map DTOs
 * only and delegate to services; X-Agent-* headers are resolved + validated here
 * (HTTP concern) via {@link AgentHeaderResolver}.
 */
@RestController
@RequestMapping("/api/v1/order")
public class OrderController {

    private static final String H_AGENT_ID = "X-Agent-ID";
    private static final String H_AGENT_STATUS = "X-Agent-Status";
    private static final String H_AGENT_COUNTRY = "X-Agent-Country";

    private final AgentHeaderResolver headerResolver;
    private final ProductCatalogService productCatalogService;
    private final PaymentChannelService paymentChannelService;
    private final PreviewOrderUseCase previewOrderUseCase;
    private final CreateOrderUseCase createOrderUseCase;

    public OrderController(AgentHeaderResolver headerResolver,
                           ProductCatalogService productCatalogService,
                           PaymentChannelService paymentChannelService,
                           PreviewOrderUseCase previewOrderUseCase,
                           CreateOrderUseCase createOrderUseCase) {
        this.headerResolver = headerResolver;
        this.productCatalogService = productCatalogService;
        this.paymentChannelService = paymentChannelService;
        this.previewOrderUseCase = previewOrderUseCase;
        this.createOrderUseCase = createOrderUseCase;
    }

    @GetMapping("/products")
    public ProductsResponse getProducts(
            @RequestHeader(value = H_AGENT_ID, required = false) String agentId,
            @RequestHeader(value = H_AGENT_STATUS, required = false) String agentStatus,
            @RequestHeader(value = H_AGENT_COUNTRY, required = false) String agentCountry) {
        AgentContext agent = headerResolver.resolve(agentId, agentStatus, agentCountry);
        List<ProductDto> products = productCatalogService.availableProducts(agent.country()).stream()
                .map(OrderController::toProductDto)
                .toList();
        return new ProductsResponse(products);
    }

    @GetMapping("/payments")
    public PaymentChannelsResponse getPayments(
            @RequestHeader(value = H_AGENT_ID, required = false) String agentId,
            @RequestHeader(value = H_AGENT_STATUS, required = false) String agentStatus,
            @RequestHeader(value = H_AGENT_COUNTRY, required = false) String agentCountry,
            @RequestParam(value = "product_id", required = false) Long productId) {
        AgentContext agent = headerResolver.resolve(agentId, agentStatus, agentCountry);
        List<PaymentChannelDto> channels =
                paymentChannelService.availableChannels(agent.country(), productId).stream()
                        .map(OrderController::toChannelDto)
                        .toList();
        return new PaymentChannelsResponse(channels);
    }

    @PostMapping("/preview")
    public PreviewOrderResponse preview(
            @RequestHeader(value = H_AGENT_ID, required = false) String agentId,
            @RequestHeader(value = H_AGENT_STATUS, required = false) String agentStatus,
            @RequestHeader(value = H_AGENT_COUNTRY, required = false) String agentCountry,
            @RequestBody(required = false) PreviewOrderRequest request) {
        AgentContext agent = headerResolver.resolve(agentId, agentStatus, agentCountry);
        return previewOrderUseCase.execute(agent, request);
    }

    @PostMapping(path = {"", "/"})
    public CreateOrderResponse create(
            @RequestHeader(value = H_AGENT_ID, required = false) String agentId,
            @RequestHeader(value = H_AGENT_STATUS, required = false) String agentStatus,
            @RequestHeader(value = H_AGENT_COUNTRY, required = false) String agentCountry,
            @RequestBody(required = false) CreateOrderRequest request) {
        AgentContext agent = headerResolver.resolve(agentId, agentStatus, agentCountry);
        return createOrderUseCase.execute(agent, request);
    }

    private static ProductDto toProductDto(ProductView p) {
        return new ProductDto(p.id(), p.name(), p.type(), p.denomination(),
                p.unitPrice(), p.imageUrl(), p.countryCode(), p.enabled());
    }

    private static PaymentChannelDto toChannelDto(ChannelView c) {
        return new PaymentChannelDto(c.id(), c.paymentMethod(), c.paymentProvider(),
                c.minAmount(), c.maxAmount(), c.countryCode(), c.enabled());
    }
}
