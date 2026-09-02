package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** One delivery attempt, for the "why did my guest not get this" list in the console. */
@Data
@Builder
public class ScheduledMessageRunResponse {
    private UUID id;
    private UUID scheduledMessageId;
    private String bookingType;
    private UUID hotelBookingId;
    private UUID activityOrderId;
    private String bookingCode;
    private UUID conversationId;
    private UUID messageId;
    private String status;
    private String detail;
    private LocalDateTime sentAt;
}
