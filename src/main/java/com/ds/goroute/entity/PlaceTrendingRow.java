package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** One place's use inside a trending window: distinct trips that added it, and check-ins. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlaceTrendingRow {
    private UUID placeId;
    private int tripCount;
    private int checkinCount;
}
