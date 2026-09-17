package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One past reassignment, with the location the check-in carried before it. */
@Data
@Builder
public class CheckinLocationHistoryResponse {

    private UUID id;
    private UUID previousPlaceId;
    private String previousPlaceName;
    private UUID newPlaceId;
    private String newPlaceName;

    private String previousLocationName;
    private String previousCustomName;
    private BigDecimal previousLatitude;
    private BigDecimal previousLongitude;
    private String previousWard;
    private String previousDistrict;
    private String previousProvince;
    private String previousProvinceCode;
    private String previousLocationSource;
    private String previousLocationKey;

    private String reason;
    private UUID changedBy;
    private String changedByName;
    private LocalDateTime changedAt;
}
