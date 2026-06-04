package com.ds.goroute.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FoodPlaceItemResponse {
    private UUID id;
    private String title;
    private String thumbnail;
    private BigDecimal adjustedRating;
    private String priceRange;
    private String address;
    private String district;
    private Integer distanceMeters;
    private BigDecimal latitude;
    private BigDecimal longitude;
}
