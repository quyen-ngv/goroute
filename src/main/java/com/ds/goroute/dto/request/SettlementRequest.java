package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import com.ds.goroute.type.SettlementMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Record (or replace) one payment from {@code from} to {@code to}, both members of the trip.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementRequest {
    @NotNull(message = "Trip is required")
    private UUID tripId;

    @NotNull(message = "Who paid is required")
    @Valid
    private LedgerParticipantRequest from;

    @NotNull(message = "Who was paid is required")
    @Valid
    private LedgerParticipantRequest to;

    /** The debt this payment clears, in {@link #currency}. */
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3)
    private String currency;

    /** Absent means OTHER. */
    private SettlementMethod method;

    /** Read by everyone in the trip. */
    @Size(max = 500)
    @ModeratedText(contentType = ModeratedContentType.EXPENSE, visibility = ModerationVisibility.GROUP)
    private String note;

    /** Absent means now. */
    private LocalDateTime paidAt;

    /** Paid in another currency: what was actually handed over. Both or neither. */
    @Positive
    private BigDecimal originalAmount;

    @Size(min = 3, max = 3)
    private String originalCurrency;
}
