package com.ds.goroute.dto.response;

import com.ds.goroute.type.CheckinLocationSource;
import com.ds.goroute.type.CheckinPhotoSource;
import com.ds.goroute.type.CheckinVerificationStatus;
import com.ds.goroute.type.ContentVisibility;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class UserCheckinResponse {

    private UUID id;
    private UUID userId;
    private String userDisplayName;
    private String userAvatarUrl;

    private UUID tripId;
    private UUID activityId;
    private UUID placeId;
    private UUID reviewId;

    private String locationName;
    private String customName;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String ward;
    private String district;
    private String province;
    private String provinceCode;
    private CheckinLocationSource locationSource;

    /**
     * The grouping key. Exposed so the app can open "other check-ins here" for a spot that
     * has no catalogue page, instead of linking to a detail screen that does not exist.
     */
    private String locationKey;

    /** Catalogue place summary, so a feed post can render the place card without a second call. */
    private String placeName;
    private String placeAddress;
    private String placeThumbnail;
    private Integer placeReviewCount;
    private BigDecimal placeReviewRating;

    /**
     * The recalculated rating when the scoring job has run for this place. Clients prefer it
     * over {@link #placeReviewRating} and fall back to the Google figure while it is null.
     */
    private BigDecimal placeAdjustedRating;

    private String caption;

    private Integer overallRating;
    private Integer foodRating;
    private Integer priceRating;
    private Integer ambianceRating;
    private Integer serviceRating;

    private CheckinPhotoSource photoSource;
    private ContentVisibility visibility;

    /**
     * Only ever {@code VERIFIED} when the evidence actually met the bar. Labelling
     * something verified that is not costs the badge its meaning everywhere it appears.
     */
    private CheckinVerificationStatus verificationStatus;
    private BigDecimal distanceMeters;

    private Integer rewardPoints;

    /** Raw stored reason. A code list since 2026-09; older rows still hold an English sentence. */
    private String rewardReason;

    /**
     * The same reason split into codes the client localises ({@code PHOTO_CAMERA},
     * {@code LOCATION_UNVERIFIED}, {@code DAILY_CAP}, ...). Sent because the author is now shown
     * why they earned what they earned at the moment they earn it, and a server-authored English
     * sentence cannot do that.
     */
    private java.util.List<String> rewardReasonCodes;

    private boolean edited;
    private LocalDateTime createdAt;

    private List<CheckinPhotoResponse> photos;

    /** True when this submission also created or updated the author's review of the place. */
    private boolean linkedToReview;

    /**
     * True only for the newest rated check-in by this author at this place. The place
     * timeline uses it to label the check-in that currently represents the author's
     * single review, while older visits remain ordinary history entries.
     */
    private boolean latestReview;

    /** Present when this check-in created or updated a place review; rendered as one post. */
    private CheckinLinkedReviewResponse linkedReview;

    private int likeCount;
    private boolean hasLiked;
}
