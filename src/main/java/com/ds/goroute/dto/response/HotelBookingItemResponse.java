package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data @Builder
public class HotelBookingItemResponse {
    private UUID id;
    private UUID roomTypeId;
    private UUID ratePlanId;
    private String roomTypeName;
    private String ratePlanName;
    private Integer quantity;
    private Integer adults;
    private Integer children;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private Map<String, Object> snapshot;
}
