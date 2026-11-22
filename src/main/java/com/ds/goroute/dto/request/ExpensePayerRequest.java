package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/** One person who paid towards an expense: {@code userId} or {@code guestMemberId}, not both. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpensePayerRequest {
    private UUID userId;
    private UUID guestMemberId;

    @NotNull(message = "Each payer needs an amount")
    @PositiveOrZero(message = "A payer amount cannot be negative")
    private BigDecimal amount;
}
