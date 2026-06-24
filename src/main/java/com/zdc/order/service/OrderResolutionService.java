package com.zdc.order.service;

import com.zdc.order.error.ErrorCode;
import com.zdc.order.error.OrderException;
import com.zdc.order.gateway.ChannelView;
import com.zdc.order.gateway.PaymentChannelGateway;
import com.zdc.order.gateway.ProductView;
import com.zdc.order.web.dto.OrderItemInput;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared validation + resolution for preview (FS-003) and create (FS-004):
 * validate items, resolve products against the catalog, compute subtotal, and
 * resolve the candidate payment channels (with product_payment_channels mapping).
 */
@Service
public class OrderResolutionService {

    private final ProductCatalogService productCatalog;
    private final PaymentChannelService channelService;
    private final PaymentChannelGateway channelGateway;

    public OrderResolutionService(ProductCatalogService productCatalog,
                                  PaymentChannelService channelService,
                                  PaymentChannelGateway channelGateway) {
        this.productCatalog = productCatalog;
        this.channelService = channelService;
        this.channelGateway = channelGateway;
    }

    /** A validated line: resolved product + merged quantity + line subtotal (whole VND). */
    public record ResolvedItem(ProductView product, int quantity, long lineSubtotal) {
    }

    public record Resolution(List<ResolvedItem> items, long subtotal, List<ChannelView> channels) {
    }

    /**
     * @param requestedChannelId required for create; optional (null = all eligible) for preview.
     */
    public Resolution resolve(List<OrderItemInput> rawItems, String countryCode, Long requestedChannelId) {
        Map<Long, Integer> mergedQuantities = validateAndMerge(rawItems);

        Map<Long, ProductView> catalog = productCatalog.availableProductsById(countryCode);
        List<ResolvedItem> items = new ArrayList<>();
        long subtotal = 0;
        for (Map.Entry<Long, Integer> e : mergedQuantities.entrySet()) {
            ProductView product = catalog.get(e.getKey());
            if (product == null) {
                throw new OrderException(ErrorCode.ERR_PRODUCT_UNAVAILABLE);
            }
            long lineSubtotal = product.unitPrice() * e.getValue();
            items.add(new ResolvedItem(product, e.getValue(), lineSubtotal));
            subtotal += lineSubtotal;
        }

        List<ChannelView> channels = resolveChannels(items, countryCode, requestedChannelId);
        return new Resolution(items, subtotal, channels);
    }

    private Map<Long, Integer> validateAndMerge(List<OrderItemInput> rawItems) {
        if (rawItems == null || rawItems.isEmpty()) {
            throw new OrderException(ErrorCode.ERR_INVALID_QUANTITY);
        }
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (OrderItemInput item : rawItems) {
            if (item == null || item.productId() == null || item.productId() <= 0
                    || item.quantity() == null || item.quantity() < 1) {
                throw new OrderException(ErrorCode.ERR_INVALID_QUANTITY);
            }
            // Merge duplicate denominations into one line (PRD AC1.3).
            merged.merge(item.productId(), item.quantity(), Integer::sum);
        }
        return merged;
    }

    private List<ChannelView> resolveChannels(List<ResolvedItem> items, String countryCode, Long requestedChannelId) {
        // Intersection of channel ids allowed for every product in the order.
        Set<Long> allowedIds = null;
        for (ResolvedItem item : items) {
            Set<Long> ids = channelGateway.findChannelIdsForProduct(item.product().id());
            if (allowedIds == null) {
                allowedIds = new java.util.HashSet<>(ids);
            } else {
                allowedIds.retainAll(ids);
            }
        }
        if (allowedIds == null) {
            allowedIds = Set.of();
        }

        List<ChannelView> countryChannels = channelService.availableChannels(countryCode, null);

        if (requestedChannelId != null) {
            if (!allowedIds.contains(requestedChannelId)) {
                throw new OrderException(ErrorCode.ERR_PRODUCT_UNAVAILABLE);
            }
            Set<Long> finalAllowed = allowedIds;
            ChannelView chosen = countryChannels.stream()
                    .filter(c -> c.id().equals(requestedChannelId) && finalAllowed.contains(c.id()))
                    .findFirst()
                    .orElseThrow(() -> new OrderException(ErrorCode.ERR_PRODUCT_UNAVAILABLE));
            return List.of(chosen);
        }

        Set<Long> finalAllowed = allowedIds;
        List<ChannelView> candidates = countryChannels.stream()
                .filter(c -> finalAllowed.contains(c.id()))
                .toList();
        if (candidates.isEmpty()) {
            throw new OrderException(ErrorCode.ERR_PRODUCT_UNAVAILABLE);
        }
        return candidates;
    }
}
