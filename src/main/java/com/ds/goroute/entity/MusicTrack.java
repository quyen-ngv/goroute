package com.ds.goroute.entity;

import com.ds.goroute.type.MusicLicense;
import com.ds.goroute.type.MusicMood;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** A background-music track a check-in can carry (V282 {@code music_tracks}). Only the link is kept. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MusicTrack {

    private UUID id;
    private String title;
    private String artist;
    /** Streamed straight from the artist's host; never copied to our storage. */
    private String audioUrl;
    /** The track's page at the source, the proof of its licence. */
    private String sourceUrl;
    private MusicLicense license;
    /** The credit line CC BY requires with the post. */
    private String attribution;
    private MusicMood mood;
    private Integer durationSeconds;
    private Integer sortOrder;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
