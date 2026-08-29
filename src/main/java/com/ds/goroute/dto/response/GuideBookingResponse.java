package com.ds.goroute.dto.response;

import com.ds.goroute.type.GuideBookingStatus;
import com.ds.goroute.type.GuidePaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A booking as both sides see it.
 *
 * <p>The fee and the guide's share are shown to everybody, not just to the platform.
 * Transparency about the commission is the condition for a guide trusting the platform at
 * all, and hiding it invites them to take the next booking off it.
 */
@Data
@Builder
public class GuideBookingResponse {
    private UUID id;
    private UUID serviceId;
    private String serviceTitle;
    private UUID guideId;
    private String guideDisplayName;
    private UUID travelerId;
    private String travelerDisplayName;
    private LocalDate bookingDate;
    private Integer guestCount;
    private String travelerNote;

    private String currency;
    private BigDecimal serviceAmount;
    private BigDecimal platformFeePercent;
    private BigDecimal platformFeeAmount;
    private BigDecimal guidePayoutAmount;
    private BigDecimal travelerTotal;

    private GuideBookingStatus status;
    private GuidePaymentStatus paymentStatus;
    private LocalDateTime respondBy;
    private LocalDateTime confirmedAt;
    private LocalDateTime completedAt;
    private LocalDateTime payoutReleasedAt;
    private boolean payoutFrozen;
    private LocalDateTime createdAt;
    private boolean reviewable;
}
