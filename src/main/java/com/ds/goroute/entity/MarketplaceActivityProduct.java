package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MarketplaceActivityProduct {
    private UUID id;
    private UUID organizationId;
    private UUID placeId;
    private String placeTitle;
    private String source;
    private String title;
    private String description;
    private String activityAddress;
    private BigDecimal priceAmount;
    private String priceCurrency;
    private String durationRaw;
    private BigDecimal durationHours;
    private Integer visitDurationMinutes;
    private String thumbnail;
    private String images;
    private String highlights;
    private String productStatus;
    private String inventoryMode;
    private Long dataVersion;
    private UUID createdBy;
    private UUID updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
