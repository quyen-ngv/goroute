package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ExpenseCategory;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

/**
 * Create or replace an expense outside any trip. With no participants it is the caller's
 * own spending; with participants it is split between registered users, Splitwise style.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalExpenseRequest {
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3)
    private String currency;

    @NotNull(message = "Category is required")
    private ExpenseCategory category;

    /** Read by the people it is split with, so filtered as group content. */
    @Size(max = 500)
    @ModeratedText(contentType = ModeratedContentType.EXPENSE, visibility = ModerationVisibility.GROUP)
    private String description;

    /** Absent means now. */
    private LocalDateTime expenseDate;

    /** Null leaves the photos alone on update; an empty list removes them. */
    @Size(max = 10)
    private List<String> photoUrls;

    /** Who paid. Absent means the caller; otherwise one of {@link #participants}. */
    private UUID paidBy;

    /** {@code EQUAL} (default) or {@code CUSTOM}, which takes each participant's amount. */
    private String splitType;

    /** Who shares the cost, each once. Absent or empty means the caller alone. */
    @Valid
    @Size(max = 20)
    private List<Participant> participants;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Participant {
        @NotNull
        private UUID userId;

        /** Their share; read only for a {@code CUSTOM} split. */
        private BigDecimal amount;
    }
}
