package com.ds.goroute.dto.request;

import com.ds.goroute.type.ExpenseCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateExpenseRequest {
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    private String currency;

    @NotNull(message = "Category is required")
    private ExpenseCategory category;

    private String description;
    private UUID activityId;
    private UUID paidBy;
    private String paidByGuestName; // Treat as both username and fullName
    private List<ExpenseSplitRequest> splits;
    private List<String> photoUrls;
}
