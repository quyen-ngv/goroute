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
    private BigDecimal exchangeRate;
    private BigDecimal amountInTripCurrency;
    private String category;
    private String description;
    private UUID activityId;
    private UserResponse paidBy;
    private UUID paidByGuestMemberId; // For guest payer
    private List<ExpenseSplitResponse> splits;
    /**
     * Flat receipt urls. Kept exactly as it was: this is what shipped, and
     * clients still read it. Built from the same media_assets rows as
     * {@link #photoUrlsV2}, so the two cannot disagree.
     */
    private List<String> photoUrls;

    /** The same receipts, with title, description, capture date and location. */
    private List<MemoryImageResponse> photoUrlsV2;

    /**
     * When the money was spent. This is the date the app shows and sorts by; {@link #createdAt}
     * stays what it always was, the moment the row was written.
     */
    private LocalDateTime expenseDate;

    private LocalDateTime createdAt;
}
