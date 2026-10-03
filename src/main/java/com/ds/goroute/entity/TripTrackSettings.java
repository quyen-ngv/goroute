package com.ds.goroute.entity;

import com.ds.goroute.type.TrackAccuracyMode;
import com.ds.goroute.type.TrackVisibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One member's tracker settings in one trip ({@code trip_track_settings}). A missing row
 * means the defaults; a row is only written once the member changes something.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripTrackSettings {

    public static final TrackVisibility DEFAULT_VISIBILITY = TrackVisibility.MEMBERS;
    public static final TrackAccuracyMode DEFAULT_ACCURACY = TrackAccuracyMode.BALANCED;

    private UUID id;
    private UUID tripId;
    private UUID userId;
    private TrackVisibility visibility;
    private Boolean trimDayEnds;
    private TrackAccuracyMode defaultAccuracy;
    private Boolean autoEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long dataVersion;

    /** The settings of a member who never changed anything; dataVersion 0 marks "no row". */
    public static TripTrackSettings defaults(UUID tripId, UUID userId) {
        return TripTrackSettings.builder()
                .tripId(tripId)
                .userId(userId)
                .visibility(DEFAULT_VISIBILITY)
                .trimDayEnds(true)
                .defaultAccuracy(DEFAULT_ACCURACY)
                .autoEnabled(false)
                .dataVersion(0L)
                .build();
    }
}
