package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * A cart line as the guest sees it: the selection they made, enough product detail to
 * recognise it without another request, and a live quote.
 *
 * <p>{@code available} is re-evaluated on every read. A line that went stale keeps its place
 * with {@code unavailableReason} set rather than disappearing, so the guest learns what
 * happened to the room they were considering.
 */
@Value
@Builder
public class CartItemResponse {
    UUID id;
    String itemType;

    /** Product title: hotel name or activity title. */
    String title;
    /** Second line: room type + rate plan, or package name. */
    String subtitle;
    String thumbnailUrl;
    /** Deep-link target on the client: the hotel id or the activity id. */
    UUID productId;

    UUID hotelId;
    UUID roomTypeId;
    UUID ratePlanId;
    LocalDate checkInDate;
    LocalDate checkOutDate;
    Integer quantity;
    Integer adults;
    Integer children;

    UUID activityId;
    UUID packageId;
    UUID slotId;
    LocalDateTime slotStartsAt;
    Map<String, Integer> unitQuantities;

    String specialRequests;

    boolean available;
    String unavailableReason;
    String currency;
    BigDecimal totalAmount;

    LocalDateTime createdAt;
}
