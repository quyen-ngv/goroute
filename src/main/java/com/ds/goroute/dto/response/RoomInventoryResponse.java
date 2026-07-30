package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder
public class RoomInventoryResponse {
    private UUID roomTypeId;
    private LocalDate inventoryDate;
    private Integer totalUnits;
    private Integer reservedUnits;
    private Integer soldUnits;
    private Integer blockedUnits;
    private Integer availableUnits;
    private Boolean stopSell;
    private BigDecimal priceOverride;
    private Integer minStay;
    private Boolean closedToArrival;
    private Boolean closedToDeparture;
    private Long dataVersion;
    private LocalDateTime updatedAt;
}
