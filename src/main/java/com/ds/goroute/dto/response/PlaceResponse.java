package com.ds.goroute.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlaceResponse {
    private UUID id;
    private String placeId;
    private String title;
    private String category;
    private String placeGroup;
    private String address;
    private List<String> destinations;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String phone;
    private String website;
    private String googleMapsLink;

    private Integer reviewCount;
    private BigDecimal reviewRating;
    private Map<String, Integer> reviewsPerRating; // {"1": 53, "2": 12, ...}

    private String thumbnail;
    private List<PlaceImagesDto> images;

    private String descriptions;
    private String priceRange;

    private Map<String, List<String>> openHours; // {"Monday": ["9 AM - 5 PM"], ...}
    private Map<String, Map<String, Integer>> popularTimes; // {"Monday": {"6": 0, "7": 17, ...}, ...}
    private List<PlaceAboutDto> about;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Distance from search location (in km)
    private Double distance;

    // Optional: include reviews
    private List<PlaceReviewResponse> reviews;

    private List<FoodTagResponse> foodTags;
}
