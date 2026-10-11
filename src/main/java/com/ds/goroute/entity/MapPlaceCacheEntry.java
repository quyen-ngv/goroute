package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One row of {@code map_place_cache}: a place a map provider described, kept for reuse. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MapPlaceCacheEntry {
    private String provider;
    private String providerPlaceId;
    private String name;
    private String nameNormalized;
    private String address;
    private Double latitude;
    private Double longitude;
    /** Comma-separated provider categories, as the app reads Goong's {@code types}. */
    private String categories;
    private String phone;
    private String website;
    private String countryCode;
    private Integer pickedCount;
}
