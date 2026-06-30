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
public class CityStoryPlaceSummary {
    private UUID id;
    private String title;
    private String thumbnail;
    private String address;
    private BigDecimal reviewRating;
    private BigDecimal adjustedRating;
    private Integer reviewCount;
}
