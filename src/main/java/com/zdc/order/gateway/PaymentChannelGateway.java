package com.zdc.order.gateway;

import java.util.List;
import java.util.Set;

/** Port to BO Service payment-channel config (FS-002). */
public interface PaymentChannelGateway {

    /** Enabled channels for a country. Fail-soft: empty list when BO is down. */
    List<ChannelView> findByCountry(String countryCode);

    /** Channel ids a product is allowed to be paid with (product_payment_channels). */
    Set<Long> findChannelIdsForProduct(Long productId);
}
