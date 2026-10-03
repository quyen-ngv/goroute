package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.PrivacyZone;
import com.ds.goroute.entity.TrackChunk;
import com.ds.goroute.entity.TrackDay;
import com.ds.goroute.entity.TrackProvince;
import com.ds.goroute.entity.TrackUserAggregate;
import com.ds.goroute.entity.TrackUserTotals;
import com.ds.goroute.entity.TrackVisit;
import com.ds.goroute.entity.TripTrackSettings;
import com.ds.goroute.mapper.TrackMapper;
import com.ds.goroute.repository.TrackRepository;
import com.ds.goroute.type.TrackVisitStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TrackRepositoryImpl implements TrackRepository {

    private final TrackMapper trackMapper;

    @Override
    public void insertChunk(TrackChunk chunk) {
        trackMapper.insertChunk(chunk);
    }

    @Override
    public List<TrackChunk> findChunksByClientId(UUID userId, UUID clientChunkId) {
        return trackMapper.selectChunksByClientId(userId, clientChunkId);
    }

    @Override
    public List<TrackChunk> findChunksForDay(UUID tripId, UUID userId, LocalDate localDate, int limit) {
        return trackMapper.selectChunksForDay(tripId, userId, localDate, limit);
    }

    @Override
    public int updateChunkPoints(TrackChunk chunk) {
        return trackMapper.updateChunkPoints(chunk);
    }

    @Override
    public int deleteChunk(UUID id, UUID userId) {
        return trackMapper.deleteChunkById(id, userId);
    }

    @Override
    public int deleteExpiredChunks(LocalDate lastTripEndDate, int limit) {
        return trackMapper.deleteExpiredChunks(lastTripEndDate, limit);
    }

    @Override
    public void upsertDay(TrackDay day) {
        trackMapper.upsertDay(day);
    }

    @Override
    public List<TrackDay> findDays(UUID tripId, List<UUID> userIds, Integer dayNumber, int limit) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return trackMapper.selectDays(tripId, userIds, dayNumber, limit);
    }

    @Override
    public List<TrackDay> findDaysForUser(UUID userId, int limit) {
        return trackMapper.selectDaysForUser(userId, limit);
    }

    @Override
    public List<LocalDate> findDayDates(UUID tripId, UUID userId) {
        return trackMapper.selectDayDates(tripId, userId);
    }

    @Override
    public List<UUID> findUserIdsWithDays(UUID tripId, int limit) {
        return trackMapper.selectUserIdsWithDays(tripId, limit);
    }

    @Override
    public void deleteDayCompletely(UUID tripId, UUID userId, LocalDate localDate) {
        trackMapper.deleteChunksForDay(tripId, userId, localDate);
        trackMapper.deleteVisitsForDay(tripId, userId, localDate);
        trackMapper.deleteDay(tripId, userId, localDate);
    }

    @Override
    public void deleteDayRow(UUID tripId, UUID userId, LocalDate localDate) {
        trackMapper.deleteVisitsForDay(tripId, userId, localDate);
        trackMapper.deleteDay(tripId, userId, localDate);
    }

    @Override
    public void replaceActivityVisits(UUID tripId, UUID userId, LocalDate localDate, List<TrackVisit> visits) {
        trackMapper.deleteActivityVisitsForDay(tripId, userId, localDate);
        for (TrackVisit visit : visits) {
            trackMapper.insertVisit(visit);
        }
    }

    @Override
    public void insertVisit(TrackVisit visit) {
        trackMapper.insertVisit(visit);
    }

    @Override
    public Optional<TrackVisit> findVisit(UUID visitId, UUID tripId, UUID userId) {
        return Optional.ofNullable(trackMapper.selectVisitById(visitId, tripId, userId));
    }

    @Override
    public int updateOffPlanStop(TrackVisit visit) {
        return trackMapper.updateOffPlanStop(visit);
    }

    @Override
    public int updateOffPlanStatus(UUID visitId, UUID userId, TrackVisitStatus status, UUID createdActivityId) {
        return trackMapper.updateOffPlanStatus(visitId, userId, status, createdActivityId);
    }

    @Override
    public int updateOffPlanNameIfMissing(UUID visitId, UUID userId, String name) {
        return trackMapper.updateOffPlanNameIfMissing(visitId, userId, name);
    }

    @Override
    public int deleteVisit(UUID visitId, UUID userId) {
        return trackMapper.deleteVisitById(visitId, userId);
    }

    @Override
    public List<String> findProvinceCodesForRoute(String wkt) {
        return trackMapper.selectProvinceCodesForRoute(wkt);
    }

    @Override
    public List<TrackProvince> findProvinces(List<String> codes) {
        if (codes.isEmpty()) {
            return List.of();
        }
        return trackMapper.selectProvincesByCodes(codes);
    }

    @Override
    public List<TrackProvince> findProvincesPassedByUser(UUID userId) {
        return trackMapper.selectProvincesPassedByUser(userId);
    }

    @Override
    public TrackUserTotals findUserTotals(UUID userId) {
        TrackUserTotals totals = trackMapper.selectUserTotals(userId);
        return totals == null ? new TrackUserTotals(0L, 0) : totals;
    }

    @Override
    public List<TrackUserAggregate> findDistanceByUser(UUID tripId, int limit) {
        return trackMapper.selectDistanceByUser(tripId, limit);
    }

    @Override
    public List<TrackUserAggregate> findLatestCheckins(UUID tripId, List<UUID> userIds, LocalDateTime fromUtc,
                                                       LocalDateTime toUtc) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return trackMapper.selectLatestCheckins(tripId, userIds, fromUtc, toUtc, userIds.size());
    }

    @Override
    public List<UUID> findCheckedInActivityIds(UUID tripId, UUID userId) {
        return trackMapper.selectCheckedInActivityIds(tripId, userId);
    }

    @Override
    public int countCheckins(UUID tripId, UUID userId) {
        return trackMapper.countCheckins(tripId, userId);
    }

    @Override
    public List<TrackVisit> findVisits(UUID tripId, List<UUID> userIds, List<LocalDate> localDates, int limit) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return trackMapper.selectVisits(tripId, userIds, localDates, limit);
    }

    @Override
    public Optional<TripTrackSettings> findSettings(UUID tripId, UUID userId) {
        return Optional.ofNullable(trackMapper.selectSettings(tripId, userId));
    }

    @Override
    public List<TripTrackSettings> findSettingsForTrip(UUID tripId, int limit) {
        return trackMapper.selectSettingsForTrip(tripId, limit);
    }

    @Override
    public void insertSettings(TripTrackSettings settings) {
        trackMapper.insertSettings(settings);
    }

    @Override
    public int updateSettings(TripTrackSettings settings, long expectedVersion) {
        return trackMapper.updateSettings(settings, expectedVersion);
    }

    @Override
    public List<PrivacyZone> findZones(UUID userId, int limit) {
        return trackMapper.selectZonesByUser(userId, limit);
    }

    @Override
    public List<PrivacyZone> findZonesForUsers(List<UUID> userIds, int limit) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return trackMapper.selectZonesByUsers(userIds, limit);
    }

    @Override
    public int countZones(UUID userId) {
        return trackMapper.countZonesByUser(userId);
    }

    @Override
    public void insertZone(PrivacyZone zone) {
        trackMapper.insertZone(zone);
    }

    @Override
    public int updateZone(PrivacyZone zone, long expectedVersion) {
        return trackMapper.updateZone(zone, expectedVersion);
    }

    @Override
    public int deleteZone(UUID id, UUID userId) {
        return trackMapper.deleteZone(id, userId);
    }

    @Override
    public void deleteTripMemberTracks(UUID tripId, UUID userId) {
        trackMapper.deleteChunksForTripUser(tripId, userId);
        trackMapper.deleteVisitsForTripUser(tripId, userId);
        trackMapper.deleteDaysForTripUser(tripId, userId);
    }

    @Override
    public void purgeTripMember(UUID tripId, UUID userId) {
        deleteTripMemberTracks(tripId, userId);
        trackMapper.deleteSettingsForTripUser(tripId, userId);
    }

    @Override
    public void purgeTrip(UUID tripId) {
        trackMapper.deleteChunksForTrip(tripId);
        trackMapper.deleteVisitsForTrip(tripId);
        trackMapper.deleteDaysForTrip(tripId);
        trackMapper.deleteSettingsForTrip(tripId);
    }

    @Override
    public void purgeUser(UUID userId) {
        purgeUserTracks(userId);
        trackMapper.deleteSettingsForUser(userId);
        trackMapper.deleteZonesForUser(userId);
    }

    @Override
    public void purgeUserTracks(UUID userId) {
        trackMapper.deleteChunksForUser(userId);
        trackMapper.deleteVisitsForUser(userId);
        trackMapper.deleteDaysForUser(userId);
    }
}
