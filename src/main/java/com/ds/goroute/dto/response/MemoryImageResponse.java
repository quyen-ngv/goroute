package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A memory image, as carried by the {@code memoryImageUrlsV2} payloads.
 *
 * <p>V2 exists because {@code memoryImageUrls} — a bare {@code List<String>} —
 * had already shipped and a display bug in production meant it could not be
 * changed. That field stays exactly as it is; everything new arrives here, and
 * a client that finds V2 empty falls back to the old list.
 *
 * <p>{@code id} and {@code uploadedBy} let a viewer decide whether the reader
 * owns the image, and which record to edit or delete, without a second call.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryImageResponse {
    private UUID id;
    private String url;

    /** The owning row in the central media catalog. */
    private String entityType;
    private UUID entityId;
    private String mediaType;
    private String assetRole;
    private Integer position;

    /** Short title. Stored as {@code media_assets.caption}. */
    private String title;

    /** Longer body under the title. Added in V135; null for older rows. */
    private String description;

    /**
     * When the shutter fired. Null when nothing better than the upload time was
     * known — clients fall back to {@link #createdAt} rather than showing a
     * date that is really an upload timestamp.
     */
    private LocalDateTime takenAt;

    /** EXIF | FILE | CAPTURE | UPLOAD | MANUAL — where {@link #takenAt} came from. */
    private String dateSource;

    /** CAMERA or GALLERY. */
    private String captureSource;

    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal accuracyMeters;

    /** The catalogued place this was taken at, when the author picked one. */
    private UUID placeId;

    /** What to show as the location; set even without a {@link #placeId}. */
    private String locationName;
    private String locationSource;

    private LocalDateTime createdAt;

    private UUID uploadedBy;
    private String uploaderName;
    private String uploaderAvatarUrl;

    /**
     * The itinerary activity this photo was attached to, when it was attached to
     * one. Trip-level memories have no activity and leave both fields null.
     *
     * <p>Carried here so a reader looking at a photo in the feed can see which
     * stop of the trip it belongs to without opening the trip.
     */
    private UUID activityId;
    private String activityName;
}
