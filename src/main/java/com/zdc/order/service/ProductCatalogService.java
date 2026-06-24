package com.zdc.order.service;

import com.zdc.order.gateway.ProductGateway;
import com.zdc.order.gateway.ProductView;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FS-001 — available products for an agent's country. Applies BR-001 (enabled +
 * country) and BR-002 (sort by unit_price ASC) on top of the gateway, which is
 * fail-soft (BR-004: empty list when BO is down).
 */
@Service
public class ProductCatalogService {

    private final ProductGateway productGateway;

    public ProductCatalogService(ProductGateway productGateway) {
        this.productGateway = productGateway;
    }

    public List<ProductView> availableProducts(String countryCode) {
        return productGateway.findByCountry(countryCode).stream()
                .filter(ProductView::enabled)
                .filter(p -> countryCode.equalsIgnoreCase(p.countryCode()))
                .sorted(Comparator.comparing(ProductView::unitPrice))
                .toList();
    }

    /** Resolved products keyed by id, insertion-ordered by unit_price ASC. */
    public Map<Long, ProductView> availableProductsById(String countryCode) {
        return availableProducts(countryCode).stream()
                .collect(LinkedHashMap::new,
                        (m, p) -> m.put(p.id(), p),
                        Map::putAll);
    }
}
