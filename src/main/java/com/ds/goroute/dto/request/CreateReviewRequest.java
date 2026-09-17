package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReviewRequest {

    private UUID placeId;

    private UUID activityBookingId;

    /**
     * Optional marketplace booking the review is written for. When one is set the server derives
     * {@code placeId} (hotel stay) or {@code activityBookingId} (activity order) from it and marks
     * the review as a verified stay/visit. At most one of the two may be present.
     */
    private UUID hotelBookingId;

    private UUID activityOrderId;

private UUID tripId; // Deprecated: reviews are scoped to place, not trip.

private BigDecimal checkinLat;
private BigDecimal checkinLng;
private BigDecimal checkinAccuracy;

    @NotNull(message = "Overall rating is required")
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    private Integer overallRating;

    // Aspect ratings (optional)
    @Min(value = 1, message = "Food rating must be between 1 and 5")
    @Max(value = 5, message = "Food rating must be between 1 and 5")
    private Integer foodRating;

    @Min(value = 1, message = "Price rating must be between 1 and 5")
    @Max(value = 5, message = "Price rating must be between 1 and 5")
    private Integer priceRating;

    @Min(value = 1, message = "Ambiance rating must be between 1 and 5")
    @Max(value = 5, message = "Ambiance rating must be between 1 and 5")
    private Integer ambianceRating;

    @Min(value = 1, message = "Service rating must be between 1 and 5")
    @Max(value = 5, message = "Service rating must be between 1 and 5")
    private Integer serviceRating;

    @Min(value = 1, message = "Location rating must be between 1 and 5")
    @Max(value = 5, message = "Location rating must be between 1 and 5")
    private Integer locationRating;

    @Min(value = 1, message = "Cleanliness rating must be between 1 and 5")
    @Max(value = 5, message = "Cleanliness rating must be between 1 and 5")
    private Integer cleanlinessRating;

    @Min(value = 1, message = "Facilities rating must be between 1 and 5")
    @Max(value = 5, message = "Facilities rating must be between 1 and 5")
    private Integer facilitiesRating;

    // Text review (optional)
    @Size(max = 2000, message = "Review text cannot exceed 2000 characters")
    @ModeratedText(contentType = ModeratedContentType.REVIEW, visibility = ModerationVisibility.PUBLIC)
    private String text;

    // Photos (optional)
    private List<String> photos;
}
