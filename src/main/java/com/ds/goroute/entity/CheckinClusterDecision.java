package com.ds.goroute.entity;

import com.ds.goroute.type.CheckinClusterDecisionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** What an operator decided about one cluster, and when. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckinClusterDecision {
    private String locationKey;
    private CheckinClusterDecisionStatus status;
    private UUID placeId;
    private UUID decidedBy;
    private LocalDateTime decidedAt;
    private String note;
    private Integer checkinCountAtDecision;
}
