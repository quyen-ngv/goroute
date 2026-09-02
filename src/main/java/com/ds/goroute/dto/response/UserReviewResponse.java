package com.ds.goroute.dto.response;

import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.UserTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserReviewResponse {
    private UUID id;
    private UUID userId;
    private UUID placeId;
    private UUID activityBookingId;
    private UUID tripId;
    
    // Place info
    private String placeName;
    private String placeAddress;
    private String placeThumbnail;
    private Integer placeReviewCount;
    private BigDecimal placeReviewRating;

    /** Recalculated rating; clients fall back to {@link #placeReviewRating} while it is null. */
    private BigDecimal placeAdjustedRating;
    private String placeCategory;
    private PlaceGroup placeGroup;
    private BigDecimal placeLatitude;
    private BigDecimal placeLongitude;
    private String placePhone;
    private String placeWebsite;
    private String placePriceRange;
    private Integer placeVisitDurationMinutes;

    // Activity booking info
    private String activityBookingTitle;
    private String activityBookingThumbnail;
    private BigDecimal activityBookingRating;
    private Integer activityBookingReviewCount;
    private BigDecimal activityBookingPriceAmount;
    private String activityBookingPriceCurrency;
    
    // User info
    private String userName;
    private String userAvatar;
    private UserTier userTier;
    
    // Ratings
    private Integer overallRating;
    private Integer foodRating;
    private Integer priceRating;
    private Integer ambianceRating;
    private Integer serviceRating;
    
    // Content
    private String text;
    private List<String> photos;
    
    // Metadata
    private BigDecimal weight;
    private Integer helpfulVotes;
    private Integer unhelpfulVotes;
    private Boolean hasVotedHelpful; // Current user voted helpful (true) or unhelpful (false) or null
    private Boolean isOwnReview; // Current user is author

    // Marketplace stay/visit link
    private Boolean verifiedStay; // true when the review is tied to a completed hotel booking or activity order
    private UUID hotelBookingId;
    private UUID activityOrderId;
    private ReviewPartnerResponse partnerResponse; // PUBLISHED partner reply, null when none

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
