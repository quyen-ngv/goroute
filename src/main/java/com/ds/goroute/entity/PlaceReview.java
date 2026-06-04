package com.ds.goroute.entity;

import com.ds.goroute.type.ReviewAuthenticityLevel;
import com.ds.goroute.type.ReviewLanguage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceReview {
    private UUID id;
    private UUID placeId;

    // Google IDs
    private String reviewId;           // Google Review ID
    private String googlePlaceId;      // Google Place ID (0x3135ab...)

    // Reviewer Info
    private String reviewerName;
    private String profileUrl;
    private String profilePicture;
    private Boolean isLocalGuide;
    private Integer totalReviews;
    private Integer totalPhotos;

    // Review Content
    private Integer rating;
    private String description;
    private ReviewLanguage language;
    private LocalDate reviewDate;

    // Images as JSON string
    private String images;

    // Engagement
    private Integer likes;

    // Data integrity
    private String contentHash;
    private Boolean isDeleted;

    // Authenticity scoring
    private BigDecimal authenticityScore;
    private ReviewAuthenticityLevel authenticityLevel;
    private LocalDateTime scoreCalculatedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
