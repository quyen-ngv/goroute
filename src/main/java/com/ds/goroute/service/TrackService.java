package com.ds.goroute.service;

import com.ds.goroute.dto.request.AddTrackVisitToItineraryRequest;
import com.ds.goroute.dto.request.PrivacyZoneRequest;
import com.ds.goroute.dto.request.TrackDeletionRequest;
import com.ds.goroute.dto.request.UpdateTrackSettingsRequest;
import com.ds.goroute.dto.request.UploadTrackChunkRequest;
import com.ds.goroute.dto.response.TrackResponses;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Travel tracker (epic 16, .docs/designs/TRAVEL_TRACKER_TECH_PLAN.md). Every method checks
 * trip membership itself; a caller never passes a pre-checked trip.
 */
public interface TrackService {

    TrackResponses.Context context(UUID userId, UUID tripId);

    /** The upload result, and whether it was a resend of a chunk already stored. */
    record UploadOutcome(TrackResponses.ChunkUpload result, boolean replayed) {
    }

    UploadOutcome uploadChunk(UUID userId, UUID tripId, UploadTrackChunkRequest request);

    /** One member's days; {@code ownerId} null means the caller's own. */
    List<TrackResponses.Day> days(UUID viewerId, UUID tripId, UUID ownerId, Integer dayNumber);

    /** Days of every member the caller may see, the caller included. */
    List<TrackResponses.Day> overview(UUID viewerId, UUID tripId, Integer dayNumber);

    TrackResponses.DayPoints dayPoints(UUID userId, UUID tripId, LocalDate date);

    TrackResponses.Deletion delete(UUID userId, UUID tripId, TrackDeletionRequest request);

    /** Hides one of the caller's own off-plan stops (P2). */
    TrackResponses.Visit dismissVisit(UUID userId, UUID tripId, UUID visitId);

    /** The result, and whether the stop had already been added before (idempotent replay). */
    record AddOutcome(TrackResponses.AddedToItinerary result, boolean alreadyAdded) {
    }

    /** Creates an activity from one of the caller's own off-plan stops (TR-16a). Needs edit access. */
    AddOutcome addVisitToItinerary(UUID userId, UUID tripId, UUID visitId, AddTrackVisitToItineraryRequest request);

    TrackResponses.Settings settings(UUID userId, UUID tripId);

    TrackResponses.Settings updateSettings(UUID userId, UUID tripId, UpdateTrackSettingsRequest request);

    List<TrackResponses.Zone> privacyZones(UUID userId);

    TrackResponses.Zone createPrivacyZone(UUID userId, PrivacyZoneRequest request);

    TrackResponses.Zone updatePrivacyZone(UUID userId, UUID zoneId, PrivacyZoneRequest request);

    void deletePrivacyZone(UUID userId, UUID zoneId);

    /** Every route the caller ever recorded, in every trip. Privacy zones are kept. */
    void deleteAllTracks(UUID userId);

    // --- lifecycle hooks called by other services -------------------------------------

    /** Account deletion: tracks, settings and privacy zones. */
    void onUserDeleted(UUID userId);

    /** Trip deletion: everything anyone recorded in it. */
    void onTripDeleted(UUID tripId);

    /** A member left or was removed: their route in that trip goes with them (TR-24). */
    void onMemberLeft(UUID tripId, UUID userId);

    /** Raw points past TRACKER/RAW_RETENTION_DAYS; one bounded batch. Returns rows removed. */
    int purgeExpiredChunks(int limit);
}
