package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseSplit {
    private UUID id;
    private UUID expenseId;
    private UUID userId;
    private UUID guestMemberId; // Reference to trip_members.id for guest
    private String guestName; // Treat as both username and fullName
    private BigDecimal amount;
    private Boolean isSettled;
    private LocalDateTime settledAt;
}
