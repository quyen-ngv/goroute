package com.ds.goroute.dto.response;

import com.ds.goroute.type.MusicLicense;
import com.ds.goroute.type.MusicMood;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/** A background-music track, in the picker and on a check-in that carries it. */
@Data
@Builder
public class MusicTrackResponse {

    private UUID id;
    private String title;
    private String artist;
    /** Streamed from the artist's host. */
    private String audioUrl;
    /** The track's page at the source. */
    private String sourceUrl;
    private MusicLicense license;
    private String licenseUrl;
    /** The credit line to show with the post. */
    private String attribution;
    private MusicMood mood;
    private Integer durationSeconds;
}
