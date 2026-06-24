package com.zdc.order.gateway.http;

import com.zdc.order.config.DownstreamProperties;
import com.zdc.order.gateway.CommercialGateway;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Commercial Service adapter (FS-003 read, FS-004 atomic charge/release). The
 * charge endpoint returns 200 with a business code when credit is insufficient;
 * that is translated to {@code ChargeResult(charged=false, availableCredit)}.
 */
@Component
public class CommercialHttpAdapter implements CommercialGateway {

    private final RestClient client;

    public CommercialHttpAdapter(RestClient.Builder builder, DownstreamProperties props) {
        this.client = builder.baseUrl(props.getCommercialUrl()).build();
    }

    record CreditWrapper(CreditInfo data) {
    }

    record ChargeResponse(Data data, String code, Map<String, Object> details) {
        record Data(boolean charged, Long availableCreditAfter) {
        }
    }

    @Override
    public CreditInfo getAvailableCredit(Long agentId) {
        CreditWrapper w = client.get()
                .uri("/commercial/{id}/available-credit", agentId)
                .header("X-Internal-Call", "true")
                .retrieve()
                .body(CreditWrapper.class);
        if (w == null || w.data() == null) {
            throw new IllegalStateException("Empty available-credit response");
        }
        return w.data();
    }

    @Override
    public ChargeResult charge(Long agentId, String orderLocalId, long amount) {
        ChargeResponse resp = client.post()
                .uri("/commercial/{id}/credit/charge", agentId)
                .header("X-Internal-Call", "true")
                .body(Map.of("order_local_id", orderLocalId, "amount", amount))
                .retrieve()
                .body(ChargeResponse.class);
        if (resp == null) {
            throw new IllegalStateException("Empty charge response");
        }
        if (resp.data() != null && resp.data().charged()) {
            return new ChargeResult(true, resp.data().availableCreditAfter());
        }
        Long available = resp.details() == null ? null
                : ((Number) resp.details().getOrDefault("available_credit", 0)).longValue();
        return new ChargeResult(false, available);
    }

    @Override
    public void release(Long agentId, String orderLocalId) {
        client.post()
                .uri("/commercial/{id}/credit/release", agentId)
                .header("X-Internal-Call", "true")
                .body(Map.of("order_local_id", orderLocalId))
                .retrieve()
                .toBodilessEntity();
    }
}
