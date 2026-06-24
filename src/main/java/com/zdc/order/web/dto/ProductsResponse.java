package com.zdc.order.web.dto;

import java.util.List;

/** FS-001 response envelope ({data:[...]}). */
public record ProductsResponse(List<ProductDto> data) {
}
