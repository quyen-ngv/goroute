package com.ds.goroute.entity;

import com.ds.goroute.type.CheckinLocationSource;
import com.ds.goroute.type.CheckinPhotoSource;
import com.ds.goroute.type.CheckinVerificationStatus;
import com.ds.goroute.type.ContentVisibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One moment: somebody was somewhere, took photos, and wrote what they thought.
 *
 * <p>Repeatable by design. Going back to a place two years later is a second check-in, not
 * an edit of the first, and both keep their own photos, caption and passport entry.
 *
 * <p>The rating fields are the deferred half of the epic's central rule. A rating belongs
 * to the <em>place</em> and a user only has one at a time, so when the place is catalogued
 * the submission also creates or updates that user's {@link UserReview} and
 * {@link #reviewId} points at it. When the place is not catalogued there is nothing to
 * average, so the score stays here until the cluster is promoted (CHK-12). Either way the
 * caption stays with this check-in and is never overwritten by a later visit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCheckin {

    private UUID id;
    private UUID userId;

    private UUID tripId;
    private UUID activityId;
    private UUID placeId;
    private UUID reviewId;

    private String locationName;
    private String customName;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal accuracyMeters;
    private String ward;
    private String district;
    private String province;
    private String provinceCode;
    private CheckinLocationSource locationSource;

    /** Rounded-coordinate grouping key; equal keys are treated as the same spot. */
    private String locationKey;

    private String caption;

    private Integer overallRating;
    private Integer foodRating;
    private Integer priceRating;
    private Integer ambianceRating;
    private Integer serviceRating;

    private CheckinPhotoSource photoSource;
    private ContentVisibility visibility;
    private CheckinVerificationStatus verificationStatus;
    private BigDecimal distanceMeters;

    private String idempotencyKey;

    private Integer rewardPoints;
    private String rewardReason;

    private Boolean isRemoved;
    private LocalDateTime editedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** True when this check-in is attached to a row in the curated catalogue. */
    public boolean hasCataloguePlace() {
        return placeId != null;
    }

    public boolean hasRating() {
        return overallRating != null;
    }
}
