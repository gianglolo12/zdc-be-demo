package com.zdc.order.service;

import com.zdc.order.gateway.ChannelView;
import com.zdc.order.gateway.PaymentChannelGateway;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * FS-002 — available payment channels for a country, optionally filtered to the
 * channels a product may be paid with (product_payment_channels). BR-001 enabled
 * + country; fail-soft via the gateway (empty list when BO is down).
 */
@Service
public class PaymentChannelService {

    private final PaymentChannelGateway channelGateway;

    public PaymentChannelService(PaymentChannelGateway channelGateway) {
        this.channelGateway = channelGateway;
    }

    public List<ChannelView> availableChannels(String countryCode, Long productId) {
        List<ChannelView> base = channelGateway.findByCountry(countryCode).stream()
                .filter(ChannelView::enabled)
                .filter(c -> countryCode.equalsIgnoreCase(c.countryCode()))
                .toList();
        if (productId == null) {
            return base;
        }
        Set<Long> allowed = channelGateway.findChannelIdsForProduct(productId);
        return base.stream().filter(c -> allowed.contains(c.id())).toList();
    }
}
