package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceReviewResponse {
    private UUID id;
    private UUID placeId;
    private String reviewerName;
    private String profilePicture;
    private String profileUrl;
    private Boolean isLocalGuide;
    private Integer totalReviews;
    private Integer totalPhotos;
    private Integer rating;
    private String description;
    private LocalDate reviewDate;
    private String images; // JSON as String
    private Integer likes;
    private BigDecimal authenticityScore;
    private String authenticityLevel;
}
