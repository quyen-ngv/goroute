package com.ds.goroute.service.impl;

import com.ds.goroute.entity.PrivacyZone;
import com.ds.goroute.entity.TrackChunk;
import com.ds.goroute.entity.TrackDay;
import com.ds.goroute.entity.TrackProvince;
import com.ds.goroute.entity.TrackUserAggregate;
import com.ds.goroute.entity.TrackUserTotals;
import com.ds.goroute.entity.TrackVisit;
import com.ds.goroute.entity.TripTrackSettings;
import com.ds.goroute.repository.TrackRepository;
import com.ds.goroute.type.TrackVisitKind;
import com.ds.goroute.type.TrackVisitStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A working {@link TrackRepository} over lists, so service tests can run real flows (upload,
 * read, delete) instead of asserting on mock calls. It mirrors the SQL: upsert on
 * (trip, user, date), version checks on updates, user_id in every user-scoped write.
 */
class InMemoryTrackRepository implements TrackRepository {

    final List<TrackChunk> chunks = new ArrayList<>();
    final List<TrackDay> days = new ArrayList<>();
    final List<TrackVisit> visits = new ArrayList<>();
    final List<TripTrackSettings> settings = new ArrayList<>();
    final List<PrivacyZone> zones = new ArrayList<>();

    @Override
    public void insertChunk(TrackChunk chunk) {
        boolean duplicate = chunks.stream().anyMatch(existing -> existing.getUserId().equals(chunk.getUserId())
                && existing.getClientChunkId().equals(chunk.getClientChunkId())
                && existing.getLocalDate().equals(chunk.getLocalDate()));
        if (duplicate) {
            throw new IllegalStateException("uq_trip_track_chunks_client");
        }
        chunk.setDataVersion(1L);
        chunks.add(chunk);
    }

    @Override
    public List<TrackChunk> findChunksByClientId(UUID userId, UUID clientChunkId) {
        return chunks.stream()
                .filter(chunk -> chunk.getUserId().equals(userId) && chunk.getClientChunkId().equals(clientChunkId))
                .toList();
    }

    @Override
    public List<TrackChunk> findChunksForDay(UUID tripId, UUID userId, LocalDate localDate, int limit) {
        return chunks.stream()
                .filter(chunk -> chunk.getTripId().equals(tripId) && chunk.getUserId().equals(userId)
                        && chunk.getLocalDate().equals(localDate))
                .sorted(Comparator.comparing(TrackChunk::getStartedAt))
                .limit(limit)
                .toList();
    }

    @Override
    public int updateChunkPoints(TrackChunk chunk) {
        for (TrackChunk existing : chunks) {
            if (existing.getId().equals(chunk.getId()) && existing.getUserId().equals(chunk.getUserId())) {
                existing.setPoints(chunk.getPoints());
                existing.setPointCount(chunk.getPointCount());
                existing.setStartedAt(chunk.getStartedAt());
                existing.setEndedAt(chunk.getEndedAt());
                existing.setDataVersion(existing.getDataVersion() + 1);
                return 1;
            }
        }
        return 0;
    }

    @Override
    public int deleteChunk(UUID id, UUID userId) {
        return chunks.removeIf(chunk -> chunk.getId().equals(id) && chunk.getUserId().equals(userId)) ? 1 : 0;
    }

    @Override
    public int deleteExpiredChunks(LocalDate lastTripEndDate, int limit) {
        throw new UnsupportedOperationException("needs the trips table");
    }

    @Override
    public void upsertDay(TrackDay day) {
        days.removeIf(existing -> sameDay(existing, day.getTripId(), day.getUserId(), day.getLocalDate()));
        days.add(day);
    }

    @Override
    public List<TrackDay> findDays(UUID tripId, List<UUID> userIds, Integer dayNumber, int limit) {
        return days.stream()
                .filter(day -> day.getTripId().equals(tripId) && userIds.contains(day.getUserId()))
                .filter(day -> dayNumber == null || dayNumber.equals(day.getDayNumber()))
                .sorted(Comparator.comparing((TrackDay day) -> day.getUserId()).thenComparing(TrackDay::getLocalDate))
                .limit(limit)
                .toList();
    }

    @Override
    public List<TrackDay> findDaysForUser(UUID userId, int limit) {
        return days.stream()
                .filter(day -> day.getUserId().equals(userId))
                .sorted(Comparator.comparing(TrackDay::getLocalDate).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<LocalDate> findDayDates(UUID tripId, UUID userId) {
        return days.stream()
                .filter(day -> day.getTripId().equals(tripId) && day.getUserId().equals(userId))
                .map(TrackDay::getLocalDate)
                .sorted()
                .toList();
    }

    @Override
    public List<UUID> findUserIdsWithDays(UUID tripId, int limit) {
        return days.stream()
                .filter(day -> day.getTripId().equals(tripId))
                .map(TrackDay::getUserId)
                .distinct()
                .limit(limit)
                .toList();
    }

    @Override
    public void deleteDayCompletely(UUID tripId, UUID userId, LocalDate localDate) {
        chunks.removeIf(chunk -> chunk.getTripId().equals(tripId) && chunk.getUserId().equals(userId)
                && chunk.getLocalDate().equals(localDate));
        deleteDayRow(tripId, userId, localDate);
    }

    @Override
    public void deleteDayRow(UUID tripId, UUID userId, LocalDate localDate) {
        visits.removeIf(visit -> visit.getTripId().equals(tripId) && visit.getUserId().equals(userId)
                && visit.getLocalDate().equals(localDate));
        days.removeIf(day -> sameDay(day, tripId, userId, localDate));
    }

    @Override
    public void replaceActivityVisits(UUID tripId, UUID userId, LocalDate localDate, List<TrackVisit> replacement) {
        visits.removeIf(visit -> visit.getTripId().equals(tripId) && visit.getUserId().equals(userId)
                && visit.getLocalDate().equals(localDate) && visit.getKind() == TrackVisitKind.ACTIVITY);
        visits.addAll(replacement);
    }

    @Override
    public List<TrackVisit> findVisits(UUID tripId, List<UUID> userIds, List<LocalDate> localDates, int limit) {
        return visits.stream()
                .filter(visit -> visit.getTripId().equals(tripId) && userIds.contains(visit.getUserId()))
                .filter(visit -> localDates == null || localDates.isEmpty() || localDates.contains(visit.getLocalDate()))
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<TripTrackSettings> findSettings(UUID tripId, UUID userId) {
        return settings.stream()
                .filter(row -> row.getTripId().equals(tripId) && row.getUserId().equals(userId))
                .findFirst();
    }

    @Override
    public List<TripTrackSettings> findSettingsForTrip(UUID tripId, int limit) {
        return settings.stream().filter(row -> row.getTripId().equals(tripId)).limit(limit).toList();
    }

    @Override
    public void insertSettings(TripTrackSettings row) {
        row.setDataVersion(1L);
        settings.add(row);
    }

    @Override
    public int updateSettings(TripTrackSettings row, long expectedVersion) {
        for (TripTrackSettings existing : settings) {
            if (existing.getId().equals(row.getId()) && existing.getUserId().equals(row.getUserId())
                    && existing.getDataVersion() == expectedVersion) {
                existing.setVisibility(row.getVisibility());
                existing.setTrimDayEnds(row.getTrimDayEnds());
                existing.setDefaultAccuracy(row.getDefaultAccuracy());
                existing.setAutoEnabled(row.getAutoEnabled());
                existing.setDataVersion(expectedVersion + 1);
                return 1;
            }
        }
        return 0;
    }

    @Override
    public List<PrivacyZone> findZones(UUID userId, int limit) {
        return zones.stream().filter(zone -> zone.getUserId().equals(userId)).limit(limit).toList();
    }

    @Override
    public List<PrivacyZone> findZonesForUsers(List<UUID> userIds, int limit) {
        return zones.stream().filter(zone -> userIds.contains(zone.getUserId())).limit(limit).toList();
    }

    @Override
    public int countZones(UUID userId) {
        return (int) zones.stream().filter(zone -> zone.getUserId().equals(userId)).count();
    }

    @Override
    public void insertZone(PrivacyZone zone) {
        zone.setDataVersion(1L);
        zones.add(zone);
    }

    @Override
    public int updateZone(PrivacyZone zone, long expectedVersion) {
        for (PrivacyZone existing : zones) {
            if (existing.getId().equals(zone.getId()) && existing.getUserId().equals(zone.getUserId())
                    && existing.getDataVersion() == expectedVersion) {
                existing.setName(zone.getName());
                existing.setLatitude(zone.getLatitude());
                existing.setLongitude(zone.getLongitude());
                existing.setRadiusM(zone.getRadiusM());
                existing.setDataVersion(expectedVersion + 1);
                return 1;
            }
        }
        return 0;
    }

    @Override
    public int deleteZone(UUID id, UUID userId) {
        return zones.removeIf(zone -> zone.getId().equals(id) && zone.getUserId().equals(userId)) ? 1 : 0;
    }

    @Override
    public void deleteTripMemberTracks(UUID tripId, UUID userId) {
        chunks.removeIf(chunk -> chunk.getTripId().equals(tripId) && chunk.getUserId().equals(userId));
        visits.removeIf(visit -> visit.getTripId().equals(tripId) && visit.getUserId().equals(userId));
        days.removeIf(day -> day.getTripId().equals(tripId) && day.getUserId().equals(userId));
    }

    @Override
    public void purgeTripMember(UUID tripId, UUID userId) {
        deleteTripMemberTracks(tripId, userId);
        settings.removeIf(row -> row.getTripId().equals(tripId) && row.getUserId().equals(userId));
    }

    @Override
    public void purgeTrip(UUID tripId) {
        chunks.removeIf(chunk -> chunk.getTripId().equals(tripId));
        visits.removeIf(visit -> visit.getTripId().equals(tripId));
        days.removeIf(day -> day.getTripId().equals(tripId));
        settings.removeIf(row -> row.getTripId().equals(tripId));
    }

    @Override
    public void purgeUser(UUID userId) {
        purgeUserTracks(userId);
        settings.removeIf(row -> row.getUserId().equals(userId));
        zones.removeIf(zone -> zone.getUserId().equals(userId));
    }

    @Override
    public void purgeUserTracks(UUID userId) {
        chunks.removeIf(chunk -> chunk.getUserId().equals(userId));
        visits.removeIf(visit -> visit.getUserId().equals(userId));
        days.removeIf(day -> day.getUserId().equals(userId));
    }

    // --- v1.1 ------------------------------------------------------------------------------

    /** What the PostGIS province query answers; tests set it. */
    List<String> provinceCodesForRoute = List.of();
    /** The last WKT the province query was asked about. */
    String lastRouteWkt;
    final List<TrackProvince> provinces = new ArrayList<>();
    final List<TrackUserAggregate> latestCheckins = new ArrayList<>();
    final List<UUID> checkedInActivityIds = new ArrayList<>();
    int checkinCount;

    @Override
    public void insertVisit(TrackVisit visit) {
        visit.setDataVersion(1L);
        visits.add(visit);
    }

    @Override
    public Optional<TrackVisit> findVisit(UUID visitId, UUID tripId, UUID userId) {
        return visits.stream()
                .filter(visit -> visit.getId().equals(visitId) && visit.getTripId().equals(tripId)
                        && visit.getUserId().equals(userId))
                .findFirst();
    }

    @Override
    public int updateOffPlanStop(TrackVisit visit) {
        for (TrackVisit existing : visits) {
            if (existing.getId().equals(visit.getId()) && existing.getUserId().equals(visit.getUserId())
                    && existing.getKind() == TrackVisitKind.OFF_PLAN) {
                existing.setLatitude(visit.getLatitude());
                existing.setLongitude(visit.getLongitude());
                existing.setArrivedAt(visit.getArrivedAt());
                existing.setDepartedAt(visit.getDepartedAt());
                existing.setName(visit.getName());
                existing.setPlaceId(visit.getPlaceId());
                return 1;
            }
        }
        return 0;
    }

    @Override
    public int updateOffPlanStatus(UUID visitId, UUID userId, TrackVisitStatus status, UUID createdActivityId) {
        for (TrackVisit existing : visits) {
            if (existing.getId().equals(visitId) && existing.getUserId().equals(userId)
                    && existing.getKind() == TrackVisitKind.OFF_PLAN) {
                existing.setStatus(status);
                existing.setCreatedActivityId(createdActivityId);
                return 1;
            }
        }
        return 0;
    }

    @Override
    public int updateOffPlanNameIfMissing(UUID visitId, UUID userId, String name) {
        for (TrackVisit existing : visits) {
            if (existing.getId().equals(visitId) && existing.getUserId().equals(userId)
                    && existing.getKind() == TrackVisitKind.OFF_PLAN && existing.getName() == null) {
                existing.setName(name);
                return 1;
            }
        }
        return 0;
    }

    @Override
    public int deleteVisit(UUID visitId, UUID userId) {
        return visits.removeIf(visit -> visit.getId().equals(visitId) && visit.getUserId().equals(userId)) ? 1 : 0;
    }

    @Override
    public List<String> findProvinceCodesForRoute(String wkt) {
        lastRouteWkt = wkt;
        return provinceCodesForRoute;
    }

    @Override
    public List<TrackProvince> findProvinces(List<String> codes) {
        return provinces.stream().filter(province -> codes.contains(province.getCode())).toList();
    }

    @Override
    public List<TrackProvince> findProvincesPassedByUser(UUID userId) {
        // The SQL unpacks the JSON arrays; a substring test is enough for test data.
        return provinces.stream()
                .filter(province -> days.stream().anyMatch(day -> day.getUserId().equals(userId)
                        && day.getProvinceCodes() != null
                        && day.getProvinceCodes().contains("\"" + province.getCode() + "\"")))
                .toList();
    }

    @Override
    public TrackUserTotals findUserTotals(UUID userId) {
        List<TrackDay> own = days.stream().filter(day -> day.getUserId().equals(userId)).toList();
        return new TrackUserTotals(own.stream().mapToLong(TrackDay::getDistanceM).sum(),
                (int) own.stream().map(TrackDay::getTripId).distinct().count());
    }

    @Override
    public List<TrackUserAggregate> findDistanceByUser(UUID tripId, int limit) {
        Map<UUID, Long> byUser = new LinkedHashMap<>();
        for (TrackDay day : days) {
            if (day.getTripId().equals(tripId)) {
                byUser.merge(day.getUserId(), (long) day.getDistanceM(), Long::sum);
            }
        }
        return byUser.entrySet().stream()
                .map(entry -> new TrackUserAggregate(entry.getKey(), entry.getValue(), null))
                .limit(limit)
                .toList();
    }

    @Override
    public List<TrackUserAggregate> findLatestCheckins(UUID tripId, List<UUID> userIds, LocalDateTime fromUtc,
                                                       LocalDateTime toUtc) {
        return latestCheckins.stream()
                .filter(row -> userIds.contains(row.getUserId())
                        && !row.getLastAt().isBefore(fromUtc) && row.getLastAt().isBefore(toUtc))
                .toList();
    }

    @Override
    public List<UUID> findCheckedInActivityIds(UUID tripId, UUID userId) {
        return checkedInActivityIds;
    }

    @Override
    public int countCheckins(UUID tripId, UUID userId) {
        return checkinCount;
    }

    private static boolean sameDay(TrackDay day, UUID tripId, UUID userId, LocalDate date) {
        return Objects.equals(day.getTripId(), tripId) && Objects.equals(day.getUserId(), userId)
                && Objects.equals(day.getLocalDate(), date);
    }
}
