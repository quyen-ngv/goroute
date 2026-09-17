package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One selection a guest parked for later. Holds no inventory: see V164 for why.
 *
 * <p>Only the columns of the matching {@code itemType} are populated; the table's shape
 * constraint enforces that, so a HOTEL row can never carry a slot id.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MarketplaceCartItem {
    private UUID id; private UUID userId; private String itemType; private String selectionHash;
    private UUID hotelId; private UUID roomTypeId; private UUID ratePlanId;
    private LocalDate checkInDate; private LocalDate checkOutDate;
    private Integer quantity; private Integer adults; private Integer children;
    private UUID activityId; private UUID packageId; private UUID slotId; private String unitQuantities;
    private String specialRequests; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
