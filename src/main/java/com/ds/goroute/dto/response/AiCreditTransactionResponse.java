package com.ds.goroute.dto.response;

import com.ds.goroute.entity.AiCreditTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One ledger entry as the app and the console show it. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCreditTransactionResponse {
    private UUID id;
    private String type;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String feature;
    private String traceId;
    private BigDecimal costUsd;
    private String note;
    private LocalDateTime createdAt;

    public static AiCreditTransactionResponse from(AiCreditTransaction tx) {
        return AiCreditTransactionResponse.builder()
                .id(tx.getId())
                .type(tx.getTransactionType())
                .amount(tx.getAmount())
                .balanceAfter(tx.getPlanBalanceAfter().add(tx.getPurchasedBalanceAfter()))
                .feature(tx.getFeature())
                .traceId(tx.getTraceId())
                .costUsd(tx.getCostUsd())
                .note(tx.getNote())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
