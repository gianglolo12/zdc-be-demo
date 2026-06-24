package com.zdc.order.service;

import com.zdc.order.gateway.ProductGateway;
import com.zdc.order.gateway.ProductView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCatalogServiceTest {

    @Mock
    ProductGateway productGateway;

    @InjectMocks
    ProductCatalogService service;

    private static ProductView product(long id, long unitPrice, boolean enabled, String country) {
        return new ProductView(id, "Thẻ Zing", "ZING", unitPrice, unitPrice, null, country, enabled);
    }

    @Test
    void sortsByUnitPriceAscAndFiltersEnabledAndCountry() {
        when(productGateway.findByCountry("vn")).thenReturn(List.of(
                product(3, 47500, true, "vn"),
                product(1, 9500, true, "vn"),
                product(9, 5000, false, "vn"),      // disabled -> filtered (BR-001)
                product(7, 1000, true, "kh"),       // other country -> filtered
                product(2, 19000, true, "VN")));     // case-insensitive country match

        List<ProductView> result = service.availableProducts("vn");

        assertThat(result).extracting(ProductView::id).containsExactly(1L, 2L, 3L);
    }

    @Test
    void emptyWhenBoUnavailable() {
        when(productGateway.findByCountry("vn")).thenReturn(List.of());
        assertThat(service.availableProducts("vn")).isEmpty();
    }

    @Test
    void productsByIdKeyedAndOrdered() {
        when(productGateway.findByCountry("vn")).thenReturn(List.of(
                product(3, 47500, true, "vn"),
                product(1, 9500, true, "vn")));

        assertThat(service.availableProductsById("vn").keySet()).containsExactly(1L, 3L);
    }
}
