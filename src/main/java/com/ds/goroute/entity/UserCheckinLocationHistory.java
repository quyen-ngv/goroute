package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * What a check-in said about its location before an operator moved it to a catalogue place.
 *
 * <p>Written on every reassignment. Without it the catalogue row would be the only thing
 * left, and the author's own account of where they were -- which is the evidence for
 * whether the reassignment was right -- would be gone.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCheckinLocationHistory {

    private UUID id;
    private UUID checkinId;

    private UUID previousPlaceId;
    private UUID newPlaceId;

    /** JSON snapshot of the location columns as they were before the change. */
    private String previousLocation;

    private String reason;
    private UUID changedBy;
    private LocalDateTime changedAt;
}
