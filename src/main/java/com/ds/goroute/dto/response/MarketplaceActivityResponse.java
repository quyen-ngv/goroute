package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data @Builder
public class MarketplaceActivityResponse {
    private UUID id; private UUID organizationId; private UUID placeId; private String placeTitle; private String title;
    private String description; private String activityAddress; private BigDecimal priceAmount; private String priceCurrency;
    private String durationRaw; private BigDecimal durationHours; private Integer visitDurationMinutes; private String thumbnail;
    private List<String> images; private List<String> highlights; private String productStatus; private String inventoryMode;
    private Long dataVersion; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
