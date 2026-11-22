package com.ds.goroute.service;

import com.ds.goroute.dto.response.MusicTrackResponse;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The background-music catalogue a check-in can pick from (V282). */
public interface MusicTrackService {

    /** The active catalogue in picker order. */
    List<MusicTrackResponse> listActive();

    /** The active tracks among these ids, by id; a switched-off track is simply absent. */
    Map<UUID, MusicTrackResponse> findActiveByIds(Collection<UUID> ids);

    /** Refuses an id that is not an active track. */
    void requireActive(UUID trackId);
}
