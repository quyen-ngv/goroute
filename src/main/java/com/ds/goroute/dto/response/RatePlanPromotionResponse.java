package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class RatePlanPromotionResponse {
    UUID id;
    UUID organizationId;
    UUID hotelId;
    UUID ratePlanId;
    String code;
    String name;
    String promotionType;
    BigDecimal discountPercent;
    Integer priority;
    LocalDate stayStart;
    LocalDate stayEnd;
    LocalDate bookStart;
    LocalDate bookEnd;
    Integer minAdvanceDays;
    Integer maxAdvanceDays;
    Integer minNights;
    List<String> daysOfWeek;
    String status;
    Long dataVersion;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
