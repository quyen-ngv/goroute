package com.ds.goroute.service;

import com.ds.goroute.dto.response.TrackResponses;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Travel tracker, phase 4: what leaves the trip's own screens (TRAVEL_TRACKER_API_CONTRACT.md
 * v1.1 section D) — the public page, the GPX file, the recap, lifetime totals, and the numbers
 * other features (push summary, Travel Book) borrow.
 */
public interface TrackSharingService {

    /**
     * Routes anyone may see on the public trip page: same visibility as the public trip itself,
     * only members who chose PUBLIC_VIEWERS, privacy zones and day ends removed, today cut at
     * the member's last check-in (TR-22). {@code viewerId} is null for an anonymous visitor.
     */
    List<TrackResponses.PublicDay> publicTrack(UUID viewerId, UUID tripId);

    /** A GPX 1.1 file of the caller's own route. */
    record GpxExport(String fileName, byte[] content) {
    }

    /** {@code date} null exports the whole trip. */
    GpxExport exportGpx(UUID userId, UUID tripId, LocalDate date);

    /** One member's totals for a trip; {@code ownerId} null means the caller. Same access as the days. */
    TrackResponses.Recap recap(UUID viewerId, UUID tripId, UUID ownerId);

    /** The caller's totals over every trip ("passed through", apart from the passport's "visited"). */
    TrackResponses.MyStats myStats(UUID userId);

    /** Every trip the caller recorded in, newest first, each route thinned for one overview map. */
    List<TrackResponses.MyTripRoute> myRoutes(UUID userId);

    /**
     * The caller's route of a trip ({@code date} null) or of one day, as anyone may see it:
     * privacy zones removed, day ends trimmed when the caller keeps that option.
     */
    TrackResponses.ShareRoute shareRoute(UUID userId, UUID tripId, LocalDate date);

    // --- read by other services, which have already checked access ---------------------

    /** The owner's route for the Travel Book, clipped by their privacy zones; empty when private or absent. */
    Optional<TrackResponses.BookRoute> bookRoute(UUID tripId, UUID ownerId);

    /** Ground distance (m) per member who recorded in this trip. */
    Map<UUID, Long> distanceByUser(UUID tripId);
}
