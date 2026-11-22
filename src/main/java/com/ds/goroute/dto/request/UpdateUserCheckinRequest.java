package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.CheckinFrameStyle;
import com.ds.goroute.type.CheckinPlaceKind;
import com.ds.goroute.type.ContentVisibility;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Editing a check-in.
 *
 * <p>The place is not editable. Moving a check-in somewhere else would mean recomputing
 * the averages of two places and rewriting a passport entry, and the history stops being
 * traceable; deleting and checking in again says the same thing without any of that.
 *
 * <p>The caption carries the same annotation as on create, so an edit goes through the
 * same filter -- otherwise editing would be the obvious way around it.
 */
@Data
public class UpdateUserCheckinRequest {

    @Size(max = 5000)
    @ModeratedText(contentType = ModeratedContentType.CHECKIN, visibility = ModerationVisibility.PUBLIC)
    private String caption;

    /**
     * The frame the photos are shown in. The edit saves the visit whole, like the caption:
     * absent clears it, so the editor always sends the one it shows.
     */
    private CheckinFrameStyle frameStyle;

    /** The background music; saved whole like the frame, so absent removes it. */
    private UUID musicTrackId;

    /** Whether a video with music plays only the music; absent keeps what is saved. */
    private Boolean muteVideoSound;

    /**
     * The kind of spot, for a check-in without a catalogue place. Unlike the frame, absent
     * keeps the one saved: an app built before this field must not wipe it on every edit.
     */
    private CheckinPlaceKind placeKind;

    @Size(max = 300)
    @ModeratedText(contentType = ModeratedContentType.CHECKIN, visibility = ModerationVisibility.PUBLIC)
    private String customName;

    @Min(1) @Max(5) private Integer overallRating;
    @Min(1) @Max(5) private Integer foodRating;
    @Min(1) @Max(5) private Integer priceRating;
    @Min(1) @Max(5) private Integer ambianceRating;
    @Min(1) @Max(5) private Integer serviceRating;

    /** Correcting the day of the visit. Null leaves the stored one alone. */
    private LocalDateTime visitedAt;

    /**
     * A check-in is a photo-backed visit, so editing replaces the ordered photo set as
     * one atomic operation: keep an existing URL, remove it, or add an uploaded one.
     */
    @NotEmpty(message = "A check-in needs at least one photo")
    @Valid
    private List<CreateUserCheckinRequest.CheckinPhotoInput> photos;

    private ContentVisibility visibility;
}
