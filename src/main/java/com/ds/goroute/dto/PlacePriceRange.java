package com.ds.goroute.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A price range per person, as a suggestion carries it: {@code {"min", "max", "currency": "VND"}}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlacePriceRange {
    private Long min;
    private Long max;
    private String currency;
}
