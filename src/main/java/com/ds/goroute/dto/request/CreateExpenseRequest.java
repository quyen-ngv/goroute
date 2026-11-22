package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import com.ds.goroute.type.ExpenseCategory;
import com.ds.goroute.type.SplitMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
    @Size(max = 100)
    private List<ExpenseSplitRequest> splits;
    private List<String> photoUrls;

    /**
     * 1 unit of {@link #currency} in the trip currency. Absent: looked up for the expense date;
     * a failed lookup refuses the expense with EXCHANGE_RATE_UNAVAILABLE rather than storing 1:1.
     */
    @Positive
    private BigDecimal exchangeRate;

    /**
     * Who paid (epic 05 §8.2). Absent: the legacy {@code paidBy} / {@code paidByGuestMemberId}
     * pays the whole amount. Present: the amounts must add up to the expense amount.
     */
    @Valid
    @Size(max = 50)
    private List<ExpensePayerRequest> payers;

    /** How to split. Absent keeps the legacy behaviour: the split amounts as sent (or splitType). */
    private SplitMethod splitMethod;
}
