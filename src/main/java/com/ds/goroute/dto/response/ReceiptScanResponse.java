package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What a receipt photo says, for the app to pre-fill the add-expense form. Every field may be
 * null; {@code readable = false} means no total was found and the user types it by hand.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptScanResponse {
    private boolean readable;
    private BigDecimal amount;
    /** ISO 4217, upper case. */
    private String currency;
    /** Shop or restaurant name as printed; the app uses it as the expense description. */
    private String merchant;
    private LocalDate spentOn;
    /** One of {@code ExpenseCategory}. */
    private String category;
    /** Other printed amounts that could be the one paid (subtotal, before/after tip); never the chosen one. */
    private List<Candidate> candidates;
    private int dailyLimit;
    private int usedToday;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Candidate {
        private String label;
        private BigDecimal amount;
    }
}
