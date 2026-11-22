package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One person who paid towards an expense; exactly one of userId / guestMemberId is set. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpensePayer {
    private UUID id;
    private UUID expenseId;
    private UUID userId;
    private UUID guestMemberId;
    private BigDecimal amount;
    private Integer position;
    private LocalDateTime createdAt;
}
