package com.zdc.order.gateway;

import java.util.List;

/** Port to BO Service product catalog (FS-001). */
public interface ProductGateway {

    /**
     * Enabled products for a country, sorted ascending by unit_price (BR-002).
     * Fail-soft: returns an empty list when BO is unavailable (BR-004).
     */
    List<ProductView> findByCountry(String countryCode);
}
