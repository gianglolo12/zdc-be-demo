package com.zdc.order.gateway.http;

import com.zdc.order.config.DownstreamProperties;
import com.zdc.order.gateway.ProductGateway;
import com.zdc.order.gateway.ProductView;
import com.zdc.order.support.OrderCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** BO Service products adapter (FS-001), Redis-cached 30d, fail-soft (BR-004). */
@Component
public class BoProductHttpAdapter implements ProductGateway {

    private static final Logger log = LoggerFactory.getLogger(BoProductHttpAdapter.class);
    private static final Duration TTL = Duration.ofDays(30);

    private final RestClient client;
    private final OrderCache cache;

    public BoProductHttpAdapter(RestClient.Builder builder, DownstreamProperties props, OrderCache cache) {
        this.client = builder.baseUrl(props.getBackOfficeUrl()).build();
        this.cache = cache;
    }

    record Wrapper(List<ProductView> data) {
    }

    @Override
    public List<ProductView> findByCountry(String countryCode) {
        String key = "order:products:" + countryCode;
        Optional<List<ProductView>> cached = cache.getList(key, ProductView.class);
        if (cached.isPresent()) {
            return cached.get();
        }
        try {
            Wrapper w = client.get()
                    .uri("/config/products?country_code={c}", countryCode)
                    .header("X-Internal-Call", "true")
                    .retrieve()
                    .body(Wrapper.class);
            List<ProductView> products = (w == null || w.data() == null) ? List.of() : w.data();
            cache.putList(key, products, TTL);
            return products;
        } catch (RuntimeException ex) {
            log.warn("BO products unavailable for country {}: {}", countryCode, ex.getMessage());
            return List.of();
        }
    }
}
