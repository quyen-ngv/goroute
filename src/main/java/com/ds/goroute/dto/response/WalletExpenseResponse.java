package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** A paged expense item with enough source context to open the original trip expense. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletExpenseResponse {
    private UUID id;
    private UUID tripId;
    private String tripName;
    private BigDecimal amount;
    private String currency;
    private String category;
    private String description;
    private UUID activityId;
    private WalletPersonResponse paidBy;
    private List<WalletExpenseSplitResponse> splits;
    private LocalDateTime createdAt;
}
