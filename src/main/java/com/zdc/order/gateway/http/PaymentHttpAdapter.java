package com.zdc.order.gateway.http;

import com.zdc.order.config.DownstreamProperties;
import com.zdc.order.gateway.PaymentGateway;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Payment Service adapter — init transaction + submit to ESP (FS-004). A PSP
 * rejection/timeout surfaces as {@link PaymentGatewayException} so the use case
 * compensates by releasing the Commercial credit.
 */
@Component
public class PaymentHttpAdapter implements PaymentGateway {

    private final RestClient client;

    public PaymentHttpAdapter(RestClient.Builder builder, DownstreamProperties props) {
        this.client = builder.baseUrl(props.getPaymentUrl()).build();
    }

    record Wrapper(PaymentInitResult data, String code) {
    }

    @Override
    public PaymentInitResult initAndSubmit(PaymentInitRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agent_id", request.agentId());
        body.put("order_local_id", request.orderLocalId());
        body.put("channel_id", request.channelId());
        body.put("payment_method", request.paymentMethod());
        body.put("payment_provider", request.paymentProvider());
        body.put("amount", request.amount());
        body.put("currency", request.currency());
        body.put("return_url", request.returnUrl());
        try {
            Wrapper w = client.post()
                    .uri("/internal/payment/init-and-submit")
                    .header("X-Internal-Call", "true")
                    .body(body)
                    .retrieve()
                    .body(Wrapper.class);
            if (w == null || w.data() == null || w.code() != null) {
                throw new PaymentGatewayException("Payment gateway returned no usable result");
            }
            return w.data();
        } catch (RestClientException ex) {
            throw new PaymentGatewayException("Payment gateway call failed", ex);
        }
    }
}
