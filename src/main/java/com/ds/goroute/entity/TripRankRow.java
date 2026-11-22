package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * One position of a public trip search ranking: the trip and its owner. The owner travels
 * with the id so a cached ranking can drop the viewer's own trips without another query.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripRankRow {
    private UUID id;
    private UUID ownerId;
}
