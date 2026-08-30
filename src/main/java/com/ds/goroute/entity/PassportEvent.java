package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Evidence that somebody was somewhere, in the form the passport reads.
 *
 * <p>Deliberately separate from both check-in tables: a check-in is an action, an event is
 * its consequence. Keeping them apart is what lets a third source be added later without
 * touching either, and it is what makes "why do I have this stamp" answerable -- every
 * event points back at the row that caused it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassportEvent {
    private UUID id;
    private UUID userId;
    private String source;
    /** The row that caused this, unique per source so a replay produces no duplicate. */
    private UUID sourceId;
    private UUID placeId;
    private UUID checkinId;
    private String locationKey;
    private String locationName;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String provinceCode;
    private LocalDateTime occurredAt;
    private Boolean isVerified;
    /** Hidden from other people, still part of the author's own history. */
    private Boolean isHidden;
    private LocalDateTime createdAt;
}
