package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One per-user number the tracker reads from outside its own rows: summed distance of a
 * trip's days, or the time of someone's latest check-in in a trip.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrackUserAggregate {
    private UUID userId;
    /** Metres; set by the distance query. */
    private Long distanceM;
    /** UTC; set by the latest check-in query. */
    private LocalDateTime lastAt;
}
