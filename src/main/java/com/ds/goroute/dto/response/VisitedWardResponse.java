package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One ward a person has checked in at, for colouring the map on their profile. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitedWardResponse {
    private String wardCode;
    private String provinceCode;
    private String wardName;
    private String provinceName;
    private int checkinCount;
    /** Check-ins proven at PLACE or WARD scope; the stronger colour on the map. */
    private int verifiedCount;
    private LocalDateTime lastCheckinAt;
}
