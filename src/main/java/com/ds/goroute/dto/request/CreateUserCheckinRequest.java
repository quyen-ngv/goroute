package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.CheckinLocationSource;
import com.ds.goroute.type.CheckinPhotoSource;
import com.ds.goroute.type.ContentVisibility;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One submission of the check-in flow: place, photos, thoughts, and optionally a rating.
 *
 * <p>Notably absent: any verification flag and any reward figure. Both are computed on the
 * server from what it can actually check, because a badge or a score the client can set is
 * one the client can invent.
 */
@Data
public class CreateUserCheckinRequest {

    /**
     * Client-generated key for this composition. Sending it twice -- a double tap, a
     * retry, a flaky connection -- returns the check-in that already exists rather than
     * creating a second one. It does not stop the same person checking in at the same
     * place again later: that is a new moment and carries a new key.
     */
    @NotBlank(message = "An idempotency key is required")
    @Size(max = 120)
    private String idempotencyKey;

    /** Set when the spot is already in the catalogue. */
    private UUID placeId;

    /** Set when checking in against an item of the trip in progress. */
    private UUID tripId;
    private UUID activityId;

    @NotBlank(message = "A location name is required")
    @Size(max = 300)
    @ModeratedText(contentType = ModeratedContentType.CHECKIN, visibility = ModerationVisibility.PUBLIC)
    private String locationName;

    /** The author's own name for the spot; sits beside the administrative name, never replaces it. */
    @Size(max = 300)
    @ModeratedText(contentType = ModeratedContentType.CHECKIN, visibility = ModerationVisibility.PUBLIC)
    private String customName;

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private BigDecimal latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private BigDecimal longitude;

    @DecimalMin(value = "0.0")
    private BigDecimal accuracyMeters;

    @Size(max = 150)
    private String ward;

    @Size(max = 150)
    private String district;

    @Size(max = 150)
    private String province;

    @Size(max = 10)
    private String provinceCode;

    @NotNull(message = "How the location was resolved is required")
    private CheckinLocationSource locationSource;

    @Size(max = 5000)
    @ModeratedText(contentType = ModeratedContentType.CHECKIN, visibility = ModerationVisibility.PUBLIC)
    private String caption;

    /**
     * The day of the visit, for someone posting after they got home. Absent means now.
     *
     * <p>It never stands in for GPS: a check-in composed days later is still measured
     * against where the phone is now, so it lands UNVERIFIED like any other distant one.
     */
    private LocalDateTime visitedAt;

    /** Optional. A check-in without a rating is a perfectly ordinary check-in. */
    @Min(1)
    @Max(5)
    private Integer overallRating;

    @Min(1) @Max(5) private Integer foodRating;
    @Min(1) @Max(5) private Integer priceRating;
    @Min(1) @Max(5) private Integer ambianceRating;
    @Min(1) @Max(5) private Integer serviceRating;

    /**
     * Check-ins are public in the current product. The field remains in the contract so
     * an "only me" mode can be introduced later without another payload migration.
     */
    private ContentVisibility visibility = ContentVisibility.PUBLIC;

    @NotEmpty(message = "A check-in needs at least one photo")
    @Valid
    private List<CheckinPhotoInput> photos;

    /** One photo, already uploaded through the shared upload door. */
    @Data
    public static class CheckinPhotoInput {

        @NotBlank(message = "A photo URL is required")
        @Size(max = 1000)
        private String url;

        /**
         * Which flow produced the photo. The app sets it from the path the file took, not
         * from anything the author chose, and the server independently refuses a gallery
         * photo when the plan does not allow one.
         */
        @NotNull(message = "The photo source is required")
        private CheckinPhotoSource source;

        /**
         * When the device says the photo was taken. Reference information only: device
         * clocks and embedded metadata are editable, so this never decides verification.
         */
        private LocalDateTime capturedAt;

        /**
         * Words about this one photo, shown as the reader swipes to it.
         *
         * <p>Moderated exactly like the check-in caption: text that reaches other
         * people goes through the same filter wherever it was typed, or the shorter
         * field becomes the obvious way around it.
         */
        @Size(max = 200)
        @ModeratedText(contentType = ModeratedContentType.CHECKIN, visibility = ModerationVisibility.PUBLIC)
        private String title;

        @Size(max = 2000)
        @ModeratedText(contentType = ModeratedContentType.CHECKIN, visibility = ModerationVisibility.PUBLIC)
        private String description;

        private BigDecimal latitude;
        private BigDecimal longitude;
        private BigDecimal accuracyMeters;
    }
}
