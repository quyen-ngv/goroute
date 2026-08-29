package com.ds.goroute.entity;

import com.ds.goroute.type.GuideBookingStatus;
import com.ds.goroute.type.GuidePaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A booking.
 *
 * <p>Every money field is copied onto the row when the booking is made and never read from
 * the current price or the current fee rule again. A guide raising their price applies to
 * the next booking, not to one somebody already agreed to -- and the same is true when the
 * platform changes its own commission.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuideBooking {
    private UUID id;
    private UUID serviceId;
    private UUID guideId;
    private UUID travelerId;
    private LocalDate bookingDate;
    private Integer guestCount;
    private String travelerNote;

    private String currency;
    private BigDecimal serviceAmount;
    private BigDecimal platformFeePercent;
    private BigDecimal platformFeeAmount;
    private BigDecimal guidePayoutAmount;
    private String feeRuleVersion;

    private GuideBookingStatus status;
    private GuidePaymentStatus paymentStatus;
    /** After this the request lapses, so a traveller is not left waiting indefinitely. */
    private LocalDateTime respondBy;
    private LocalDateTime confirmedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String cancellationReason;
    private LocalDateTime disputeOpenedAt;
    private LocalDateTime payoutReleasedAt;
    private Boolean payoutFrozen;

    private String idempotencyKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
