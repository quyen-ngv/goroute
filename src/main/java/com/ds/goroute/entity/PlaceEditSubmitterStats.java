package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** How a submitter's earlier suggestions went: APPROVED/PARTIALLY_APPROVED vs REJECTED. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlaceEditSubmitterStats {
    private long approvedCount;
    private long rejectedCount;
}
