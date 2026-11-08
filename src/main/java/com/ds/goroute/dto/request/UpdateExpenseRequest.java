package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import com.ds.goroute.type.ExpenseCategory;
import com.ds.goroute.type.SplitMethod;
import jakarta.validation.Valid;
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
// TODO add a service instead
public class UpdateExpenseRequest {
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @Size(min = 3, max = 3, message = "Currency must be a 3-letter code")
    private String currency;

    private ExpenseCategory category;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    @ModeratedText(contentType = ModeratedContentType.EXPENSE, visibility = ModerationVisibility.GROUP)
    private String description;

    /** Null leaves the stored date alone, like every other field on this request. */
    private LocalDateTime expenseDate;

    private UUID activityId;

    private String splitType;

    private List<UUID> splitWith;

    @Size(max = 100)
    private List<ExpenseSplitRequest> splits;

    @Positive
    private BigDecimal exchangeRate;
    
    private List<String> photoUrls;
    
    private UUID paidById;
    
    private UUID paidByGuestMemberId;

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
