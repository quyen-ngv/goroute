package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Projection for one "count per trip" row of a grouped query, so a page of trip cards gets
 * its member, activity or memory counts in one query instead of one per trip.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripCount {
    private UUID tripId;
    private Integer itemCount;
}
