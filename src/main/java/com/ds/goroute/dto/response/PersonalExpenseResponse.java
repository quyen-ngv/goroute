package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** An expense outside any trip, as its creator, payer and the people it is split with see it. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalExpenseResponse {
    private UUID id;
    private BigDecimal amount;
    private String currency;
    private String category;
    private String description;
    private LocalDateTime expenseDate;
    private List<String> photoUrls;
    private LocalDateTime createdAt;
    private WalletPersonResponse createdBy;
    private WalletPersonResponse paidBy;
    /** EQUAL when every share is the even split of the amount, CUSTOM otherwise. */
    private String splitType;
    private List<Split> splits;
    /** Only the creator edits or deletes. */
    private boolean canEdit;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Split {
        private UUID id;
        private WalletPersonResponse person;
        private BigDecimal amount;
        /** Boxed so the JSON key stays isPaid, as on trip expense splits. */
        private Boolean isPaid;
    }
}
