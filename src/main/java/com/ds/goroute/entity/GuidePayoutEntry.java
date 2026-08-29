package com.ds.goroute.entity;

import com.ds.goroute.type.GuidePayoutEntryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One line of the guide money ledger. Append-only; corrections are new entries. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuidePayoutEntry {
    private UUID id;
    private UUID bookingId;
    private UUID guideId;
    private GuidePayoutEntryType entryType;
    private BigDecimal amount;
    private String currency;
    private UUID reversesEntryId;
    /** Idempotency key; the same event replayed writes one line, not two. */
    private String referenceKey;
    private String note;
    private UUID createdBy;
    private LocalDateTime createdAt;
}
