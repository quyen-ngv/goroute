package com.ds.goroute.dto.response;

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
public class ExpenseResponse {
    private UUID id;
    private BigDecimal amount;
    private String currency;
    private String category;
    private String description;
    private UUID activityId;
    private UserResponse paidBy;
    private UUID paidByGuestMemberId; // For guest payer
    private List<ExpenseSplitResponse> splits;
    private List<String> photoUrls;
    private LocalDateTime createdAt;
}
