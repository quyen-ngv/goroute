package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ActivityOrderItem {
    private UUID id; private UUID orderId; private UUID packageId; private String packageName; private Integer quantity;
    private BigDecimal unitPrice; private BigDecimal totalPrice; private String snapshot; private LocalDateTime createdAt;
}
