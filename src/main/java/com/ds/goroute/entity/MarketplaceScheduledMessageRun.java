package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One delivery attempt of a scheduled message rule against one booking. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceScheduledMessageRun {
    private UUID id;
    private UUID scheduledMessageId;
    private String bookingType;
    private UUID hotelBookingId;
    private UUID activityOrderId;
    private UUID conversationId;
    private UUID messageId;
    private LocalDateTime sentAt;
    private String status;
    private String detail;
    /** Projected from the booking for the debugging list. */
    private String bookingCode;
}
