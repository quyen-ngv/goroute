package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * What the composer needs to know the moment a place is chosen (CHK-01, CHK-07).
 *
 * <p>The important field is {@link #existingRating}: from the second visit onwards the
 * star control has to say "you gave this 4 stars in March; rating again replaces that"
 * before the author touches it. Silently overwriting an opinion somebody wrote last year,
 * because they happened to check in again, is how a product loses trust -- and they would
 * only ever find out by noticing a score they do not remember changing.
 */
@Data
@Builder
public class CheckinContextResponse {

    private boolean checkinEnabled;

    /** Whether this spot is in the curated catalogue; decides whether a rating counts now. */
    private boolean placeInCatalogue;

    private UUID existingReviewId;
    private String existingReviewText;
    private Integer existingRating;
    private Integer existingFoodRating;
    private Integer existingPriceRating;
    private Integer existingAmbianceRating;
    private Integer existingServiceRating;
    private LocalDateTime existingRatingAt;

    /** Aspect ratings only make sense for some kinds of place; food scores on a mountain do not. */
    private boolean showAspectRatings;

    private int previousCheckinCount;

    private boolean galleryAllowed;
    private int maxPhotos;
    private int maxCaptionLength;
    private int verifyRadiusMeters;
    private int maxAccuracyMeters;

    private boolean guideScreenEnabled;
    private int guideScreenMaxViews;

    /** Multipliers, so the guidance screen states the real numbers rather than a guess. */
    private double cameraRewardMultiplier;
    private double galleryRewardMultiplier;
}
