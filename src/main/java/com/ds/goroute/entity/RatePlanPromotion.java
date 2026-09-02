package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RatePlanPromotion {
    private UUID id;
    private UUID organizationId;
    private UUID hotelId;
    /** Null means the promotion applies to every rate plan of the hotel. */
    private UUID ratePlanId;
    private String code;
    private String name;
    private String promotionType;
    private BigDecimal discountPercent;
    private Integer priority;
    private LocalDate stayStart;
    private LocalDate stayEnd;
    private LocalDate bookStart;
    private LocalDate bookEnd;
    private Integer minAdvanceDays;
    private Integer maxAdvanceDays;
    private Integer minNights;
    /** JSON array of day names, e.g. ["FRIDAY","SATURDAY"]; empty means every day. */
    private String daysOfWeek;
    private String status;
    private Long dataVersion;
    private UUID createdBy;
    private UUID updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
