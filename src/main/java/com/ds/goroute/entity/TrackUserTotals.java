package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Everything one user recorded, across every trip: ground distance and number of trips. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrackUserTotals {
    private Long totalDistanceM;
    private Integer tripCount;
}
