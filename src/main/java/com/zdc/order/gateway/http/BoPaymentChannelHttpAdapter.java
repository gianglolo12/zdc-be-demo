package com.zdc.order.gateway.http;

import com.zdc.order.config.DownstreamProperties;
import com.zdc.order.gateway.ChannelView;
import com.zdc.order.gateway.PaymentChannelGateway;
import com.zdc.order.support.OrderCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** BO Service payment-channel + product-channel mapping adapter (FS-002), cached 30d. */
@Component
public class BoPaymentChannelHttpAdapter implements PaymentChannelGateway {

    private static final Logger log = LoggerFactory.getLogger(BoPaymentChannelHttpAdapter.class);
    private static final Duration TTL = Duration.ofDays(30);

    private final RestClient client;
    private final OrderCache cache;

    public BoPaymentChannelHttpAdapter(RestClient.Builder builder, DownstreamProperties props, OrderCache cache) {
        this.client = builder.baseUrl(props.getBackOfficeUrl()).build();
        this.cache = cache;
    }

    record ChannelWrapper(List<ChannelView> data) {
    }

    record Mapping(Long productId, Long paymentChannelId, boolean enabled) {
    }

    record MappingWrapper(List<Mapping> data) {
    }

    @Override
    public List<ChannelView> findByCountry(String countryCode) {
        String key = "order:payment-channels:" + countryCode;
        Optional<List<ChannelView>> cached = cache.getList(key, ChannelView.class);
        if (cached.isPresent()) {
            return cached.get();
        }
        try {
            ChannelWrapper w = client.get()
                    .uri("/config/payment-channels?country_code={c}", countryCode)
                    .header("X-Internal-Call", "true")
                    .retrieve()
                    .body(ChannelWrapper.class);
            List<ChannelView> channels = (w == null || w.data() == null) ? List.of() : w.data();
            cache.putList(key, channels, TTL);
            return channels;
        } catch (RuntimeException ex) {
            log.warn("BO payment-channels unavailable for country {}: {}", countryCode, ex.getMessage());
            return List.of();
        }
    }

    @Override
    public Set<Long> findChannelIdsForProduct(Long productId) {
        String key = "order:product-payment-channels:" + productId;
        Optional<List<Mapping>> cached = cache.getList(key, Mapping.class);
        List<Mapping> mappings;
        if (cached.isPresent()) {
            mappings = cached.get();
        } else {
            try {
                MappingWrapper w = client.get()
                        .uri("/config/product-payment-channels?product_id={p}", productId)
                        .header("X-Internal-Call", "true")
                        .retrieve()
                        .body(MappingWrapper.class);
                mappings = (w == null || w.data() == null) ? List.of() : w.data();
                cache.putList(key, mappings, TTL);
            } catch (RuntimeException ex) {
                log.warn("BO product-payment-channels unavailable for product {}: {}", productId, ex.getMessage());
                return Set.of();
            }
        }
        return mappings.stream()
                .filter(Mapping::enabled)
                .map(Mapping::paymentChannelId)
                .collect(Collectors.toSet());
    }
}
