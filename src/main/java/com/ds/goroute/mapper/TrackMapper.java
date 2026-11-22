package com.ds.goroute.mapper;

import com.ds.goroute.entity.PrivacyZone;
import com.ds.goroute.entity.TrackChunk;
import com.ds.goroute.entity.TrackDay;
import com.ds.goroute.entity.TrackProvince;
import com.ds.goroute.entity.TrackUserAggregate;
import com.ds.goroute.entity.TrackUserTotals;
import com.ds.goroute.entity.TrackVisit;
import com.ds.goroute.entity.TripTrackSettings;
import com.ds.goroute.type.TrackVisitStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Travel tracker tables (V202, V203). SQL in {@code resources/mapper/TrackMapper.xml}. */
@Mapper
public interface TrackMapper {

    // --- chunks ---------------------------------------------------------------------

    void insertChunk(TrackChunk chunk);

    List<TrackChunk> selectChunksByClientId(@Param("userId") UUID userId,
                                            @Param("clientChunkId") UUID clientChunkId);

    List<TrackChunk> selectChunksForDay(@Param("tripId") UUID tripId,
                                        @Param("userId") UUID userId,
                                        @Param("localDate") LocalDate localDate,
                                        @Param("limit") int limit);

    List<TrackDay> selectDaysForUser(@Param("userId") UUID userId, @Param("limit") int limit);

    int updateChunkPoints(TrackChunk chunk);

    int deleteChunkById(@Param("id") UUID id, @Param("userId") UUID userId);

    int deleteChunksForDay(@Param("tripId") UUID tripId,
                           @Param("userId") UUID userId,
                           @Param("localDate") LocalDate localDate);

    int deleteChunksForTripUser(@Param("tripId") UUID tripId, @Param("userId") UUID userId);

    int deleteChunksForTrip(@Param("tripId") UUID tripId);

    int deleteChunksForUser(@Param("userId") UUID userId);

    int deleteExpiredChunks(@Param("lastTripEndDate") LocalDate lastTripEndDate,
                            @Param("limit") int limit);

    // --- days -------------------------------------------------------------------------

    void upsertDay(TrackDay day);

    List<TrackDay> selectDays(@Param("tripId") UUID tripId,
                              @Param("userIds") List<UUID> userIds,
                              @Param("dayNumber") Integer dayNumber,
                              @Param("limit") int limit);

    List<LocalDate> selectDayDates(@Param("tripId") UUID tripId, @Param("userId") UUID userId);

    List<UUID> selectUserIdsWithDays(@Param("tripId") UUID tripId, @Param("limit") int limit);

    int deleteDay(@Param("tripId") UUID tripId,
                  @Param("userId") UUID userId,
                  @Param("localDate") LocalDate localDate);

    int deleteDaysForTripUser(@Param("tripId") UUID tripId, @Param("userId") UUID userId);

    int deleteDaysForTrip(@Param("tripId") UUID tripId);

    int deleteDaysForUser(@Param("userId") UUID userId);

    // --- visits -----------------------------------------------------------------------

    void insertVisit(TrackVisit visit);

    List<TrackVisit> selectVisits(@Param("tripId") UUID tripId,
                                  @Param("userIds") List<UUID> userIds,
                                  @Param("localDates") List<LocalDate> localDates,
                                  @Param("limit") int limit);

    int deleteActivityVisitsForDay(@Param("tripId") UUID tripId,
                                   @Param("userId") UUID userId,
                                   @Param("localDate") LocalDate localDate);

    int deleteVisitsForDay(@Param("tripId") UUID tripId,
                           @Param("userId") UUID userId,
                           @Param("localDate") LocalDate localDate);

    int deleteVisitsForTripUser(@Param("tripId") UUID tripId, @Param("userId") UUID userId);

    int deleteVisitsForTrip(@Param("tripId") UUID tripId);

    int deleteVisitsForUser(@Param("userId") UUID userId);

    TrackVisit selectVisitById(@Param("id") UUID id, @Param("tripId") UUID tripId, @Param("userId") UUID userId);

    int updateOffPlanStop(TrackVisit visit);

    int updateOffPlanStatus(@Param("id") UUID id,
                            @Param("userId") UUID userId,
                            @Param("status") TrackVisitStatus status,
                            @Param("createdActivityId") UUID createdActivityId);

    int updateOffPlanNameIfMissing(@Param("id") UUID id, @Param("userId") UUID userId, @Param("name") String name);

    int deleteVisitById(@Param("id") UUID id, @Param("userId") UUID userId);

    // --- provinces and totals (P4) ----------------------------------------------------

    List<String> selectProvinceCodesForRoute(@Param("wkt") String wkt);

    List<TrackProvince> selectProvincesByCodes(@Param("codes") List<String> codes);

    List<TrackProvince> selectProvincesPassedByUser(@Param("userId") UUID userId);

    TrackUserTotals selectUserTotals(@Param("userId") UUID userId);

    List<TrackUserAggregate> selectDistanceByUser(@Param("tripId") UUID tripId, @Param("limit") int limit);

    // --- check-ins, read-only -----------------------------------------------------------

    List<TrackUserAggregate> selectLatestCheckins(@Param("tripId") UUID tripId,
                                                  @Param("userIds") List<UUID> userIds,
                                                  @Param("fromUtc") LocalDateTime fromUtc,
                                                  @Param("toUtc") LocalDateTime toUtc,
                                                  @Param("limit") int limit);

    List<UUID> selectCheckedInActivityIds(@Param("tripId") UUID tripId, @Param("userId") UUID userId);

    int countCheckins(@Param("tripId") UUID tripId, @Param("userId") UUID userId);

    // --- settings ---------------------------------------------------------------------

    TripTrackSettings selectSettings(@Param("tripId") UUID tripId, @Param("userId") UUID userId);

    List<TripTrackSettings> selectSettingsForTrip(@Param("tripId") UUID tripId, @Param("limit") int limit);

    void insertSettings(TripTrackSettings settings);

    int updateSettings(@Param("settings") TripTrackSettings settings,
                       @Param("expectedVersion") long expectedVersion);

    int deleteSettingsForTripUser(@Param("tripId") UUID tripId, @Param("userId") UUID userId);

    int deleteSettingsForTrip(@Param("tripId") UUID tripId);

    int deleteSettingsForUser(@Param("userId") UUID userId);

    // --- privacy zones ----------------------------------------------------------------

    List<PrivacyZone> selectZonesByUser(@Param("userId") UUID userId, @Param("limit") int limit);

    List<PrivacyZone> selectZonesByUsers(@Param("userIds") List<UUID> userIds, @Param("limit") int limit);

    int countZonesByUser(@Param("userId") UUID userId);

    void insertZone(PrivacyZone zone);

    int updateZone(@Param("zone") PrivacyZone zone, @Param("expectedVersion") long expectedVersion);

    int deleteZone(@Param("id") UUID id, @Param("userId") UUID userId);

    int deleteZonesForUser(@Param("userId") UUID userId);
}
