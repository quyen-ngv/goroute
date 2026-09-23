package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import com.ds.goroute.type.ExpenseCategory;
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

    private List<ExpenseSplitRequest> splits;

    private BigDecimal exchangeRate;
    
    private List<String> photoUrls;
    
    private UUID paidById;
    
    private UUID paidByGuestMemberId;
}
