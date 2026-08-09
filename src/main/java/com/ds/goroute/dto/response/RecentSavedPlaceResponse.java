package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

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
    private List<SavedPlaceCategoryResponse> categories;
    private LocalDateTime savedAt;
}
