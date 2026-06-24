package com.zdc.order.gateway.http;

import com.zdc.order.config.DownstreamProperties;
import com.zdc.order.gateway.PricingGateway;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Pricing Service adapter (ADR-016/017). Not cached (money-sensitive). Failures
 * propagate as RuntimeException and the use case maps them to ERR_INTERNAL (502).
 */
@Component
public class PricingHttpAdapter implements PricingGateway {

    private final RestClient client;

    public PricingHttpAdapter(RestClient.Builder builder, DownstreamProperties props) {
        this.client = builder.baseUrl(props.getPricingUrl()).build();
    }

    record Wrapper(List<ChannelPricing> data) {
    }

    @Override
    public List<ChannelPricing> calculate(PricingRequest request) {
        Wrapper w = client.post()
                .uri("/pricing/calculate")
                .header("X-Internal-Call", "true")
                .body(request)
                .retrieve()
                .body(Wrapper.class);
        return (w == null || w.data() == null) ? List.of() : w.data();
    }
}
