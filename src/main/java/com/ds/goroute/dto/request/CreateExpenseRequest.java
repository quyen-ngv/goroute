package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import com.ds.goroute.type.ExpenseCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class CreateExpenseRequest {
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    private String currency;

    @NotNull(message = "Category is required")
    private ExpenseCategory category;

    @ModeratedText(contentType = ModeratedContentType.EXPENSE, visibility = ModerationVisibility.GROUP)
    private String description;
    /**
     * When the money was spent. Absent means now, which is what a client that has not
     * shipped the date picker yet sends, and what most in-the-moment entries mean anyway.
     */
    private LocalDateTime expenseDate;

    private UUID activityId;
    private UUID paidBy;
    private String paidByGuestName; // Treat as both username and fullName
    private UUID paidByGuestMemberId; // Reference to trip_members.id for guest payer
    private List<ExpenseSplitRequest> splits;
    private List<String> photoUrls;
}
