package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CreateTripMemoryRequest {
    @NotBlank
    private String url;
    private UUID activityId;
    /** Short title shown over the image in the viewer. */
    @ModeratedText(contentType = ModeratedContentType.TRIP_MEMORY, visibility = ModerationVisibility.GROUP)
    private String caption;

    /** Longer body under the title. Optional. */
    @ModeratedText(contentType = ModeratedContentType.TRIP_MEMORY, visibility = ModerationVisibility.GROUP)
    private String description;

    /**
     * When the photo was taken, read off the file by the client.
     *
     * <p>Reference information, like the check-in photo's {@code capturedAt}:
     * EXIF is editable and the device clock is whatever the device says, so
     * {@code created_at} remains the server's own record of when this arrived.
     */
    private LocalDateTime takenAt;

    /** EXIF | FILE | CAPTURE | UPLOAD | MANUAL — where {@link #takenAt} came from. */
    private String dateSource;

    /** CAMERA or GALLERY. */
    private String captureSource;

    private BigDecimal latitude;
    private BigDecimal longitude;

    /**
     * The catalogued place the author picked, when they picked one.
     *
     * <p>Optional in every entry point: a memory is a photo first. A place makes it
     * findable later, it is not what makes it valid.
     */
    private UUID placeId;

    /** What to show as the location. Kept even when {@link #placeId} is null. */
    @Size(max = 255)
    @ModeratedText(contentType = ModeratedContentType.TRIP_MEMORY, visibility = ModerationVisibility.GROUP)
    private String locationName;

    /**
     * CATALOGUE_PLACE | TRIP_ACTIVITY | MAP_SEARCH | REVERSE_GEOCODE | USER_NAMED |
     * COORDINATES_ONLY -- the same vocabulary a check-in target uses.
     */
    @Size(max = 30)
    private String locationSource;
}
