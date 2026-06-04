package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewInput {

    @NotBlank(message = "reviewId is required")
    private String reviewId;

    @NotBlank(message = "googlePlaceId is required")
    private String googlePlaceId;

    @NotBlank(message = "authorName is required")
    private String authorName;

    private String profileUrl;
    private String profilePicture;

    @NotNull(message = "isLocalGuide is required")
    private Boolean isLocalGuide;

    @NotNull(message = "totalReviews is required")
    private Integer totalReviews;

    @NotNull(message = "totalPhotos is required")
    private Integer totalPhotos;

    @NotNull(message = "rating is required")
    @Min(value = 1, message = "rating must be between 1 and 5")
    @Max(value = 5, message = "rating must be between 1 and 5")
    private Integer rating;

    private Map<String, String> reviewText;  // {"en": "...", "ja": "..."}

    @NotBlank(message = "reviewDate is required")
    private String reviewDate;  // ISO 8601 format

    private List<String> userImages;

    private Integer likes;

    @NotBlank(message = "contentHash is required")
    private String contentHash;

    @NotNull(message = "isDeleted is required")
    private Boolean isDeleted;
}
