package com.ds.goroute.service;

import com.ds.goroute.dto.request.TripModerationRequest;
import com.ds.goroute.dto.response.AdminPublicTripResponse;
import com.ds.goroute.type.TripVisibility;

import java.util.List;
import java.util.UUID;

/**
 * Admin console for shared trips (PUBLIC and SHARED "Link only"): browse them, hide/show one,
 * or take one out of public view, without waiting for a user report.
 */
public interface TripAdminService {

    /** @param visibility PUBLIC or SHARED; null for both */
    List<AdminPublicTripResponse> list(String search, UUID ownerId, TripVisibility visibility, Boolean hidden,
                                       int page, int size);

    long count(String search, UUID ownerId, TripVisibility visibility, Boolean hidden);

    /**
     * Takes the trip down: it leaves the feed, search, profiles, Place Detail and its public link.
     * The owner and members keep the trip itself. Reversible with {@link #show}.
     */
    void hide(UUID adminId, UUID tripId, TripModerationRequest request);

    /** Reverses a hide decision. */
    void show(UUID adminId, UUID tripId);

    /**
     * Sets the trip to PRIVATE. Members keep using it; the owner may share it again (a standing
     * takedown keeps it hidden if they do). The owner is told why.
     */
    void unpublish(UUID adminId, UUID tripId, TripModerationRequest request);
}
