package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaAsset {
    private UUID id;
    private UUID tripId;
    private UUID activityId;
    private String entityType;
    private UUID entityId;
    private String mediaType;
    private String url;
    /** Logical role inside an entity, e.g. PHOTO or RECEIPT. */
    private String assetRole;

    /** Stable display order within the owning entity. */
    private Integer position;

    /** Short title. Column has been named `caption` since V046, so it stays. */
    private String caption;

    /** Longer body under the title. Added in V135; null for everything older. */
    private String description;

    /**
     * When the shutter fired, as far as we can tell. Null when nothing better
     * than the upload time was available — readers fall back to
     * {@link #createdAt} rather than inventing a date.
     */
    private LocalDateTime takenAt;

    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal accuracyMeters;

    /** CAMERA or GALLERY: how the file reached the app. */
    private String captureSource;

    /** EXIF | FILE | CAPTURE | UPLOAD | MANUAL — provenance of {@link #takenAt}. */
    private String dateSource;

    /**
     * The catalogued place this was taken at, when the author picked one.
     *
     * <p>Null is normal: a map result, a reverse-geocoded current position and a photo
     * with no location at all all live here as a null place with, at most, a
     * {@link #locationName}.
     */
    private UUID placeId;

    /** What to show as the location. Set even when {@link #placeId} is null. */
    private String locationName;

    /** Where the location came from; same vocabulary as a check-in target. */
    private String locationSource;

    private UUID uploadedBy;

    /** When the row was written. Never adjusted; auditing reads this. */
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
