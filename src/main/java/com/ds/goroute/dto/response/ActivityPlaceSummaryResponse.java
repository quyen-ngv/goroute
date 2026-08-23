package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityPlaceSummaryResponse {
    private UUID id;
    private String placeId;
    private String name;
    private String address;
    private BigDecimal lat;
    private BigDecimal lng;
    private String category;
    private String placeGroup;
    private BigDecimal rating;
    private Integer reviewCount;
    private String thumbnail;
}
