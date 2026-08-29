package com.ds.goroute.dto.response;

import com.ds.goroute.type.CheckinClusterDecisionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** One row of the promotion queue (CHK-12). */
@Data
@Builder
public class CheckinClusterResponse {
    private String locationKey;
    private String commonName;
    private List<String> alternateNames;
    private BigDecimal centroidLatitude;
    private BigDecimal centroidLongitude;
    private String ward;
    private String district;
    private String province;
    private String provinceCode;
    private int checkinCount;
    /** Ranked on this first: many visits by many people is a place, by one person a habit. */
    private int distinctUserCount;
    private int ratedCheckinCount;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private List<String> samplePhotos;
    private CheckinClusterDecisionStatus decisionStatus;
    private UUID decisionPlaceId;
}
