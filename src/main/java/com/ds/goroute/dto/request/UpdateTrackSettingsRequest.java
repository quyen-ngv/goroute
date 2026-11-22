package com.ds.goroute.dto.request;

import com.ds.goroute.type.TrackAccuracyMode;
import com.ds.goroute.type.TrackVisibility;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * The caller's tracker settings in one trip. {@code expectedVersion} is the version the
 * screen showed; 0 means "I was looking at the defaults".
 */
@Data
public class UpdateTrackSettingsRequest {

    @NotNull
    private TrackVisibility visibility;

    @NotNull
    private Boolean trimDayEnds;

    @NotNull
    private TrackAccuracyMode defaultAccuracy;

    /** P3 automatic recording in this trip; null keeps the stored value. */
    private Boolean autoEnabled;

    @NotNull
    @Min(0)
    private Long expectedVersion;
}
