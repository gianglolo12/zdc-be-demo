package com.zdc.order.web;

import com.zdc.order.error.ErrorCode;
import com.zdc.order.error.OrderException;
import com.zdc.order.gateway.ProductView;
import com.zdc.order.service.CreateOrderUseCase;
import com.zdc.order.service.PaymentChannelService;
import com.zdc.order.service.PreviewOrderUseCase;
import com.zdc.order.service.ProductCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@ActiveProfiles("test")
@Import(AgentHeaderResolver.class)
class OrderControllerTest {

    @Autowired MockMvc mvc;

    @MockBean ProductCatalogService productCatalogService;
    @MockBean PaymentChannelService paymentChannelService;
    @MockBean PreviewOrderUseCase previewOrderUseCase;
    @MockBean CreateOrderUseCase createOrderUseCase;

    @Test
    void missingHeaderReturns400InvalidRequest() throws Exception {
        mvc.perform(get("/api/v1/order/products"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_INVALID_REQUEST"));
    }

    @Test
    void nonActiveAgentReturns403() throws Exception {
        mvc.perform(get("/api/v1/order/products")
                        .header("X-Agent-ID", "555")
                        .header("X-Agent-Status", "SUSPENDED")
                        .header("X-Agent-Country", "vn"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_AGENT_NOT_ACTIVE"));
    }

    @Test
    void productsReturnedAsSnakeCaseEnvelope() throws Exception {
        when(productCatalogService.availableProducts("vn")).thenReturn(List.of(
                new ProductView(1L, "Thẻ Zing 10.000đ", "ZING", 10000L, 9500L,
                        "https://cdn/zing-10k.png", "vn", true)));

        mvc.perform(get("/api/v1/order/products")
                        .header("X-Agent-ID", "555")
                        .header("X-Agent-Status", "ACTIVE")
                        .header("X-Agent-Country", "vn"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].unit_price").value(9500))
                .andExpect(jsonPath("$.data[0].country_code").value("vn"));
    }

    @Test
    void creditInsufficientReturns200WithBusinessEnvelope() throws Exception {
        when(createOrderUseCase.execute(any(), any())).thenThrow(new OrderException(
                ErrorCode.ERR_CREDIT_INSUFFICIENT, ErrorCode.ERR_CREDIT_INSUFFICIENT.message(),
                Map.of("available_credit", 3_000_000L)));

        mvc.perform(post("/api/v1/order/")
                        .header("X-Agent-ID", "555")
                        .header("X-Agent-Status", "ACTIVE")
                        .header("X-Agent-Country", "vn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"product_id\":2,\"quantity\":100}],\"payment_channel_id\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ERR_CREDIT_INSUFFICIENT"))
                .andExpect(jsonPath("$.details.available_credit").value(3_000_000));
    }
}
