package com.ds.goroute.entity;

import com.ds.goroute.type.ExpenseCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Expense {
    private UUID id;
    private UUID tripId;
    private UUID activityId;
    private BigDecimal amount;
    private String currency;
    private BigDecimal exchangeRate;
    private BigDecimal amountInTripCurrency;
    private ExpenseCategory category;
    private String description;
    private UUID paidBy;
    private String paidByGuestName; // Treat as both username and fullName
    private UUID paidByGuestMemberId; // Reference to trip_members.id for guest payer
    private String receiptUrl;
    private String[] photoUrls;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
