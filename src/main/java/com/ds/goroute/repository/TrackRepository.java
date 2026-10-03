package com.ds.goroute.repository;

import com.ds.goroute.entity.PrivacyZone;
import com.ds.goroute.entity.TrackChunk;
import com.ds.goroute.entity.TrackDay;
import com.ds.goroute.entity.TrackProvince;
import com.ds.goroute.entity.TrackUserAggregate;
import com.ds.goroute.entity.TrackUserTotals;
import com.ds.goroute.entity.TrackVisit;
import com.ds.goroute.entity.TripTrackSettings;
import com.ds.goroute.type.TrackVisitStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence for the travel tracker (V202/V203 tables). */
public interface TrackRepository {

    void insertChunk(TrackChunk chunk);

    List<TrackChunk> findChunksByClientId(UUID userId, UUID clientChunkId);

    List<TrackChunk> findChunksForDay(UUID tripId, UUID userId, LocalDate localDate, int limit);

    int updateChunkPoints(TrackChunk chunk);

    int deleteChunk(UUID id, UUID userId);

    int deleteExpiredChunks(LocalDate lastTripEndDate, int limit);

    void upsertDay(TrackDay day);

    List<TrackDay> findDays(UUID tripId, List<UUID> userIds, Integer dayNumber, int limit);

    /** Every day one person recorded, across trips, newest first. */
    List<TrackDay> findDaysForUser(UUID userId, int limit);

    List<LocalDate> findDayDates(UUID tripId, UUID userId);

    List<UUID> findUserIdsWithDays(UUID tripId, int limit);

    /** Removes the day row, its chunks and its visits. */
    void deleteDayCompletely(UUID tripId, UUID userId, LocalDate localDate);

    void deleteDayRow(UUID tripId, UUID userId, LocalDate localDate);

    /** Replaces the day's ACTIVITY visits; OFF_PLAN stops are left alone. */
    void replaceActivityVisits(UUID tripId, UUID userId, LocalDate localDate, List<TrackVisit> visits);

    void insertVisit(TrackVisit visit);

    /** One of the caller's own visits in this trip. */
    Optional<TrackVisit> findVisit(UUID visitId, UUID tripId, UUID userId);

    /** New position, times, name and place of an off-plan stop; its status is untouched. */
    int updateOffPlanStop(TrackVisit visit);

    int updateOffPlanStatus(UUID visitId, UUID userId, TrackVisitStatus status, UUID createdActivityId);

    int updateOffPlanNameIfMissing(UUID visitId, UUID userId, String name);

    int deleteVisit(UUID visitId, UUID userId);

    List<TrackVisit> findVisits(UUID tripId, List<UUID> userIds, List<LocalDate> localDates, int limit);

    // --- provinces and totals (P4) ----------------------------------------------------

    /** Codes of the active provinces a route geometry (WKT, SRID 4326) intersects, ordered. */
    List<String> findProvinceCodesForRoute(String wkt);

    List<TrackProvince> findProvinces(List<String> codes);

    List<TrackProvince> findProvincesPassedByUser(UUID userId);

    TrackUserTotals findUserTotals(UUID userId);

    /** Summed ground distance per person who recorded in this trip. */
    List<TrackUserAggregate> findDistanceByUser(UUID tripId, int limit);

    // --- check-ins (read-only) --------------------------------------------------------

    /** Latest check-in time (UTC) per person in this trip within [fromUtc, toUtc). */
    List<TrackUserAggregate> findLatestCheckins(UUID tripId, List<UUID> userIds, LocalDateTime fromUtc,
                                                LocalDateTime toUtc);

    List<UUID> findCheckedInActivityIds(UUID tripId, UUID userId);

    int countCheckins(UUID tripId, UUID userId);

    Optional<TripTrackSettings> findSettings(UUID tripId, UUID userId);

    List<TripTrackSettings> findSettingsForTrip(UUID tripId, int limit);

    void insertSettings(TripTrackSettings settings);

    int updateSettings(TripTrackSettings settings, long expectedVersion);

    List<PrivacyZone> findZones(UUID userId, int limit);

    List<PrivacyZone> findZonesForUsers(List<UUID> userIds, int limit);

    int countZones(UUID userId);

    void insertZone(PrivacyZone zone);

    int updateZone(PrivacyZone zone, long expectedVersion);

    int deleteZone(UUID id, UUID userId);

    /** Every recorded point, day and visit of one member in one trip; settings are kept. */
    void deleteTripMemberTracks(UUID tripId, UUID userId);

    /** Everything one member recorded in one trip, including their settings. */
    void purgeTripMember(UUID tripId, UUID userId);

    /** Everything recorded by anyone in one trip. */
    void purgeTrip(UUID tripId);

    /** Every track, setting and privacy zone of one user. */
    void purgeUser(UUID userId);

    /** Every track of one user, keeping their privacy zones. */
    void purgeUserTracks(UUID userId);
}
