package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data @Builder
public class ActivityOrderItemResponse {
    private UUID id; private UUID packageId; private String packageName; private Integer quantity;
    private BigDecimal unitPrice; private BigDecimal totalPrice; private Map<String,Object> snapshot;
}
