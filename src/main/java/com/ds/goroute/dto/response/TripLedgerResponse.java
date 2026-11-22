package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A trip's balances (epic 05 §8.3), one block per currency the trip's money was in, never
 * converted. {@code net = paid − owed + settledOut − settledIn}; the nets of a block add up to 0.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripLedgerResponse {
    private UUID tripId;
    private Boolean simplifyDebts;
    private List<CurrencyBlock> currencies;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrencyBlock {
        private String currency;
        private List<Member> members;
        /** Simplified when simplifyDebts, else the person-to-person debts netted both ways. */
        private List<Transfer> transfers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Member {
        private WalletPersonResponse participant;
        private BigDecimal paid;
        private BigDecimal owed;
        private BigDecimal settledOut;
        private BigDecimal settledIn;
        private BigDecimal net;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Transfer {
        private WalletPersonResponse from;
        private WalletPersonResponse to;
        private BigDecimal amount;
    }
}
