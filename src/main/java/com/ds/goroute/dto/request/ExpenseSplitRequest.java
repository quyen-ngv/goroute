package com.ds.goroute.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseSplitRequest {
    private UUID userId;
    private UUID guestMemberId; // Reference to trip_members.id for guest
    private String guestName; // Treat as both username and fullName
    private BigDecimal amount;
}
