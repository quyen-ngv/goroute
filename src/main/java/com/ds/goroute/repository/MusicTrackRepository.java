package com.ds.goroute.repository;

import com.ds.goroute.entity.MusicTrack;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Persistence of the background-music catalogue (V282 {@code music_tracks}). */
public interface MusicTrackRepository {

    /** The active catalogue in picker order, at most {@code limit} rows. */
    List<MusicTrack> findActive(int limit);

    /** The active tracks among these ids; empty input reads nothing. */
    List<MusicTrack> findActiveByIds(Collection<UUID> ids);
}
