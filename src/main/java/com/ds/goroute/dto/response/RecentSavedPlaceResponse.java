package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentSavedPlaceResponse {
    private String placeId;
    private String name;
    private String address;
    private Double lat;
    private Double lng;
    private Double rating;
    private String photoUrl;
    /** The kind of place (restaurant, museum…), from the catalogue. */
    private String placeCategory;
    /** The curated tourist area the place lies in; null until the area auto-map resolves one. */
    private UUID locationImageId;
    /** That area's full address, e.g. "Đà Nẵng". */
    private String locationImageName;
    private List<SavedPlaceCategoryResponse> categories;
    private LocalDateTime savedAt;
}
