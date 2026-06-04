package com.ds.goroute.entity;

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
public class FoodPlaceRow {
    private UUID id;
    private String title;
    private String thumbnail;
    private BigDecimal adjustedRating;
    private String priceRange;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Double distanceMeters;
}
