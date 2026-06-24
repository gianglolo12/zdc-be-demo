package com.zdc.order.service;

import com.zdc.order.gateway.ChannelView;
import com.zdc.order.gateway.PaymentChannelGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentChannelServiceTest {

    @Mock
    PaymentChannelGateway gateway;

    @InjectMocks
    PaymentChannelService service;

    private static ChannelView channel(long id, boolean enabled, String country) {
        return new ChannelView(id, "VIET_QR", "ZALOPAY", 10000L, 50_000_000L, country, enabled);
    }

    @Test
    void returnsEnabledCountryChannelsWhenNoProductFilter() {
        when(gateway.findByCountry("vn")).thenReturn(List.of(
                channel(1, true, "vn"),
                channel(2, false, "vn"),   // disabled
                channel(3, true, "kh")));  // other country

        assertThat(service.availableChannels("vn", null)).extracting(ChannelView::id).containsExactly(1L);
    }

    @Test
    void filtersByProductMappingWhenProductIdGiven() {
        when(gateway.findByCountry("vn")).thenReturn(List.of(
                channel(1, true, "vn"),
                channel(2, true, "vn")));
        when(gateway.findChannelIdsForProduct(5L)).thenReturn(Set.of(2L));

        assertThat(service.availableChannels("vn", 5L)).extracting(ChannelView::id).containsExactly(2L);
    }
}
