package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.TrackPointsPayload;
import com.ds.goroute.dto.request.AddTrackVisitToItineraryRequest;
import com.ds.goroute.dto.request.CreateActivityRequest;
import com.ds.goroute.dto.request.PrivacyZoneRequest;
import com.ds.goroute.dto.request.TrackDeletionRequest;
import com.ds.goroute.dto.request.UpdateTrackSettingsRequest;
import com.ds.goroute.dto.request.UploadTrackChunkRequest;
import com.ds.goroute.dto.response.ActivityResponse;
import com.ds.goroute.dto.response.TrackResponses;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.PrivacyZone;
import com.ds.goroute.entity.TrackChunk;
import com.ds.goroute.entity.TrackDay;
import com.ds.goroute.entity.TrackVisit;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.entity.TripTrackSettings;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.TrackRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.ActivityService;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.TrackService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.TripRealtimePublisher;
import com.ds.goroute.service.track.PrivacyZoneClipper;
import com.ds.goroute.service.track.TrackDayBuilder;
import com.ds.goroute.service.track.TrackDayResult;
import com.ds.goroute.service.track.TrackPoint;
import com.ds.goroute.service.track.TrackPointsCodec;
import com.ds.goroute.service.track.TrackRouteWkt;
import com.ds.goroute.service.track.TrackStayDetector;
import com.ds.goroute.service.track.TrackStopNamer;
import com.ds.goroute.service.track.TrackSegmentLine;
import com.ds.goroute.service.track.TrackVisitDetector;
import com.ds.goroute.service.track.TrackerSettings;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.MemberStatus;
import com.ds.goroute.type.TrackAccuracyMode;
import com.ds.goroute.type.TrackVisibility;
import com.ds.goroute.type.TrackVisitKind;
import com.ds.goroute.type.TrackVisitStatus;
import com.ds.goroute.type.TripRealtimeEventType;
import com.ds.goroute.type.TripVisibility;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Travel tracker, phase 1 (.docs/designs/TRAVEL_TRACKER_TECH_PLAN.md §1).
 *
 * <p>Uploads are idempotent per {@code clientChunkId}. After every change to a person's
 * points on a day, that day is rebuilt from <em>all</em> of its chunks — statistics, the
 * simplified line and the planned activities reached — so a late offline upload or a
 * deletion can never leave stale numbers behind. The server never trusts a number computed
 * on the phone.
 *
 * <p>Privacy zones are applied when a route is read by someone other than its owner, never
 * when it is stored.
 *
 * <p>v1.1: a rebuild also records the provinces the line passes through (one PostGIS query)
 * and the off-plan stops (P2). A stop that has no catalogue place nearby is named by Goong
 * <em>after</em> the transaction commits, on a worker thread, so a slow geocoder can neither
 * hold a database connection nor fail an upload.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrackServiceImpl implements TrackService {

    static final ZoneId DEFAULT_TRIP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    static final int DAY_CHUNK_LIMIT = 2_000;
    private static final int MEMBER_LIMIT = 200;
    private static final int DAY_LIMIT = 1_000;
    private static final int VISIT_LIMIT = 5_000;
    private static final int ZONE_LIMIT = 100;
    private static final int MAX_ZONES = 5;
    static final int MAX_DAY_POINTS = 10_000;
    /** Off-plan stops kept per person and day; a day with more is noise, not a plan. */
    private static final int MAX_STOPS_PER_DAY = 50;
    /** Reverse geocodes one upload or deletion may trigger. */
    private static final int MAX_GEOCODES_PER_CHANGE = 5;
    /** Suggested start times snap to this many minutes. */
    private static final int START_TIME_STEP_MINUTES = 5;
    /** A stored chunk is never re-validated against the upload cap. */
    private static final int STORED_CHUNK_MAX_POINTS = Integer.MAX_VALUE;

    private static final TypeReference<List<TrackResponses.Segment>> SEGMENTS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> PROVINCE_CODES_TYPE = new TypeReference<>() {
    };

    private final TrackRepository trackRepository;
    private final TripAccessGuard tripAccessGuard;
    private final TripMemberRepository tripMemberRepository;
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final ActivityService activityService;
    private final TrackStopNamer stopNamer;
    private final BusinessConfigService businessConfig;
    private final BetaAccessService betaAccess;
    private final TripRealtimePublisher realtimePublisher;
    private final ObjectMapper objectMapper;

    /** Replaced in tests. */
    Clock clock = Clock.systemUTC();

    // --- context ----------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public TrackResponses.Context context(UUID userId, UUID tripId) {
        Trip trip = tripAccessGuard.requireAccess(tripId, userId);
        boolean enabled = isEnabled(userId);
        ZoneId zone = zoneOf(trip);
        int padDays = businessConfig.getInt(BusinessConfigKey.TRACKER_WINDOW_PAD_DAYS);
        boolean canRecord = enabled && inWindow(trip, LocalDate.now(clock.withZone(zone)), padDays);

        Map<UUID, TripTrackSettings> settingsByUser = settingsByUser(tripId);
        TripTrackSettings mine = settingsByUser.getOrDefault(userId, TripTrackSettings.defaults(tripId, userId));
        Set<UUID> withTrack = new HashSet<>(trackRepository.findUserIdsWithDays(tripId, MEMBER_LIMIT));

        List<UUID> memberIds = acceptedMemberIds(trip);
        Map<UUID, User> users = new HashMap<>();
        for (User user : userRepository.findByIds(memberIds)) {
            users.put(user.getId(), user);
        }
        List<TrackResponses.Member> members = new ArrayList<>();
        for (UUID memberId : memberIds) {
            User user = users.get(memberId);
            boolean isMe = memberId.equals(userId);
            members.add(new TrackResponses.Member(
                    memberId,
                    user == null ? null : displayName(user),
                    user == null ? null : user.getAvatarUrl(),
                    isMe,
                    withTrack.contains(memberId),
                    isMe || isVisibleToMembers(settingsByUser.get(memberId))));
        }

        return TrackResponses.Context.builder()
                .enabled(enabled)
                .canRecord(canRecord)
                .window(new TrackResponses.Window(trip.getStartDate(), trip.getEndDate(), zone.getId(), padDays))
                .config(config())
                .mySettings(toResponse(mine))
                .members(members)
                .build();
    }

    // --- upload -----------------------------------------------------------------------

    @Override
    @Transactional
    public UploadOutcome uploadChunk(UUID userId, UUID tripId, UploadTrackChunkRequest request) {
        Trip trip = tripAccessGuard.requireAccess(tripId, userId);
        requireEnabled(userId);

        List<TrackChunk> existing = trackRepository.findChunksByClientId(userId, request.getClientChunkId());
        if (!existing.isEmpty()) {
            if (existing.stream().anyMatch(chunk -> !tripId.equals(chunk.getTripId()))) {
                throw new BusinessException(ErrorConstant.TRACK_CHUNK_INVALID,
                        "clientChunkId was already used for another trip");
            }
            return new UploadOutcome(replayResult(trip, userId, existing), true);
        }

        List<TrackPoint> points = decode(request.getPoints(),
                businessConfig.getInt(BusinessConfigKey.TRACKER_CHUNK_MAX_POINTS));
        ZoneId zone = zoneOf(trip);
        int padDays = businessConfig.getInt(BusinessConfigKey.TRACKER_WINDOW_PAD_DAYS);
        TrackerSettings settings = trackerSettings();
        int maxAccuracy = settings.maxAccuracyFor(request.getAccuracyMode());

        Map<LocalDate, List<TrackPoint>> byDate = new TreeMap<>();
        int rejected = 0;
        int outsideWindow = 0;
        for (TrackPoint point : points) {
            if (!isPlausibleCoordinate(point) || (point.accuracyM() != null && point.accuracyM() > maxAccuracy)) {
                rejected++;
                continue;
            }
            LocalDate date = Instant.ofEpochMilli(point.timeMs()).atZone(zone).toLocalDate();
            if (!inWindow(trip, date, padDays)) {
                rejected++;
                outsideWindow++;
                continue;
            }
            byDate.computeIfAbsent(date, ignored -> new ArrayList<>()).add(point);
        }
        if (byDate.isEmpty() && outsideWindow > 0) {
            throw new BusinessException(ErrorConstant.TRACK_OUTSIDE_TRIP_WINDOW,
                    "No point falls within the days of this trip");
        }

        int accepted = 0;
        boolean first = true;
        for (Map.Entry<LocalDate, List<TrackPoint>> entry : byDate.entrySet()) {
            List<TrackPoint> dayPoints = entry.getValue();
            accepted += dayPoints.size();
            trackRepository.insertChunk(TrackChunk.builder()
                    .id(UUID.randomUUID())
                    .tripId(tripId)
                    .userId(userId)
                    .clientChunkId(request.getClientChunkId())
                    .clientSessionId(request.getClientSessionId())
                    .localDate(entry.getKey())
                    .startedAt(utc(dayPoints.get(0).timeMs()))
                    .endedAt(utc(dayPoints.get(dayPoints.size() - 1).timeMs()))
                    .pointCount(dayPoints.size())
                    .rejectedCount(first ? rejected : 0)
                    .accuracyMode(request.getAccuracyMode())
                    .source(request.getSource())
                    .points(writeJson(TrackPointsCodec.encode(dayPoints)))
                    .build());
            first = false;
        }

        List<TrackResponses.DaySummary> summaries = new ArrayList<>();
        List<UnnamedStop> unnamed = new ArrayList<>();
        for (LocalDate date : byDate.keySet()) {
            TrackDay day = rebuildDay(trip, userId, date, settings, unnamed);
            if (day != null) {
                summaries.add(toSummary(day));
            }
        }
        publishUpdated(tripId, userId, byDate.keySet());
        nameStopsAfterCommit(tripId, userId, unnamed);
        return new UploadOutcome(new TrackResponses.ChunkUpload(accepted, rejected, summaries), false);
    }

    private TrackResponses.ChunkUpload replayResult(Trip trip, UUID userId, List<TrackChunk> chunks) {
        int accepted = 0;
        int rejected = 0;
        Set<LocalDate> dates = new LinkedHashSet<>();
        for (TrackChunk chunk : chunks) {
            accepted += nullToZero(chunk.getPointCount());
            rejected += nullToZero(chunk.getRejectedCount());
            dates.add(chunk.getLocalDate());
        }
        List<TrackResponses.DaySummary> summaries = new ArrayList<>();
        for (TrackDay day : trackRepository.findDays(trip.getId(), List.of(userId), null, DAY_LIMIT)) {
            if (dates.contains(day.getLocalDate())) {
                summaries.add(toSummary(day));
            }
        }
        return new TrackResponses.ChunkUpload(accepted, rejected, summaries);
    }

    /**
     * Rebuilds one person's day from all its chunks. Returns the stored day, or null when no
     * point is left and the day was removed. Off-plan stops that still need a name from Goong
     * are added to {@code unnamed}.
     */
    private TrackDay rebuildDay(Trip trip, UUID userId, LocalDate date, TrackerSettings settings,
                                List<UnnamedStop> unnamed) {
        List<TrackPoint> points = new ArrayList<>();
        for (TrackChunk chunk : trackRepository.findChunksForDay(trip.getId(), userId, date, DAY_CHUNK_LIMIT)) {
            points.addAll(readChunk(chunk));
        }
        TrackDayResult result = TrackDayBuilder.build(points, settings);
        if (result.isEmpty()) {
            trackRepository.deleteDayRow(trip.getId(), userId, date);
            return null;
        }
        int dayNumber = dayNumberOf(trip, date);
        TrackDay day = TrackDay.builder()
                .id(UUID.randomUUID())
                .tripId(trip.getId())
                .userId(userId)
                .localDate(date)
                .dayNumber(dayNumber)
                .lineSegments(writeJson(toSegments(result.segments())))
                .bbox(result.bbox() == null ? null : writeJson(result.bbox()))
                .distanceM(result.distanceM())
                .flightDistanceM(result.flightDistanceM())
                .movingSeconds(result.movingSeconds())
                .elevationGainM(result.elevationGainM())
                .elevationLossM(result.elevationLossM())
                .pointCount(result.pointCount())
                .provinceCodes(writeJson(provinceCodes(result)))
                .firstAt(utc(result.firstAtMs()))
                .lastAt(utc(result.lastAtMs()))
                .build();
        trackRepository.upsertDay(day);
        List<Activity> planned = plannedActivities(trip, dayNumber);
        trackRepository.replaceActivityVisits(trip.getId(), userId, date,
                detectVisits(trip, userId, date, planned, result.keptPoints(), settings));
        unnamed.addAll(reconcileStops(trip, userId, date, dayNumber, planned, result.keptPoints(), settings));
        return day;
    }

    /**
     * Provinces the day's line crosses, one indexed PostGIS query. The line is the simplified
     * one (at most {@link TrackDayBuilder#MAX_LINE_POINTS} points), so the query stays small.
     */
    private List<String> provinceCodes(TrackDayResult result) {
        String wkt = TrackRouteWkt.build(result.segments(), result.bbox());
        return wkt == null ? List.of() : trackRepository.findProvinceCodesForRoute(wkt);
    }

    /** The day's activities that are a place (coordinates, and not a transport leg). */
    private List<Activity> plannedActivities(Trip trip, int dayNumber) {
        if (dayNumber < 1) {
            return List.of();
        }
        List<Activity> planned = new ArrayList<>();
        for (Activity activity : activityRepository.findByTripIdAndDayNumber(trip.getId(), dayNumber)) {
            // A transport leg has a start and an end; standing at its origin is not a visit.
            if (isPlace(activity)) {
                planned.add(activity);
            }
        }
        return planned;
    }

    static boolean isPlace(Activity activity) {
        return activity.getLat() != null && activity.getLng() != null
                && activity.getEndLat() == null && activity.getEndLng() == null;
    }

    private List<TrackVisit> detectVisits(Trip trip, UUID userId, LocalDate date, List<Activity> planned,
                                          List<TrackPoint> points, TrackerSettings settings) {
        Map<UUID, Activity> activities = new HashMap<>();
        List<TrackVisitDetector.Target> targets = new ArrayList<>();
        for (Activity activity : planned) {
            activities.put(activity.getId(), activity);
            targets.add(new TrackVisitDetector.Target(
                    activity.getId(), activity.getLat().doubleValue(), activity.getLng().doubleValue()));
        }
        List<TrackVisit> visits = new ArrayList<>();
        for (TrackVisitDetector.Visit visit : TrackVisitDetector.detect(
                points, targets, settings.arrivalRadiusM(), settings.arrivalMinutes())) {
            Activity activity = activities.get(visit.targetId());
            visits.add(TrackVisit.builder()
                    .id(UUID.randomUUID())
                    .tripId(trip.getId())
                    .userId(userId)
                    .localDate(date)
                    .kind(TrackVisitKind.ACTIVITY)
                    .activityId(activity.getId())
                    .name(activity.getName())
                    .latitude(activity.getLat())
                    .longitude(activity.getLng())
                    .arrivedAt(utc(visit.arrivedMs()))
                    .departedAt(visit.departedMs() == null ? null : utc(visit.departedMs()))
                    .status(TrackVisitStatus.VISITED)
                    .build());
        }
        return visits;
    }

    /** An off-plan stop the map provider should name once the transaction has committed. */
    record UnnamedStop(UUID visitId, double latitude, double longitude) {
    }

    /**
     * Brings the day's off-plan stops in line with the points (TR-16). A stop found again —
     * centre within the radius and overlapping times — keeps its row, id and status, so a
     * DISMISSED or ADDED stop stays that way; a SUGGESTED stop no longer found is deleted.
     * Stops are only proposed on the trip's own days, never on the padding days around it.
     */
    private List<UnnamedStop> reconcileStops(Trip trip, UUID userId, LocalDate date, int dayNumber,
                                             List<Activity> planned, List<TrackPoint> points,
                                             TrackerSettings settings) {
        int radiusM = businessConfig.getInt(BusinessConfigKey.TRACKER_OFFPLAN_RADIUS_M);
        List<TrackStayDetector.Stay> stays = List.of();
        if (dayNumber >= 1 && dayNumber <= dayCountOf(trip)) {
            List<TrackStayDetector.Excluded> excluded = planned.stream()
                    .map(activity -> new TrackStayDetector.Excluded(
                            activity.getLat().doubleValue(), activity.getLng().doubleValue()))
                    .toList();
            stays = TrackStayDetector.detect(points, new TrackStayDetector.Rules(
                    radiusM,
                    businessConfig.getInt(BusinessConfigKey.TRACKER_OFFPLAN_MIN_MINUTES),
                    settings.arrivalRadiusM(),
                    settings.gapMinutes(),
                    settings.gapDistanceM()), excluded);
            if (stays.size() > MAX_STOPS_PER_DAY) {
                stays = stays.subList(0, MAX_STOPS_PER_DAY);
            }
        }
        List<TrackVisit> existing = trackRepository.findVisits(trip.getId(), List.of(userId), List.of(date),
                        VISIT_LIMIT).stream()
                .filter(visit -> visit.getKind() == TrackVisitKind.OFF_PLAN)
                .toList();
        int nameRadiusM = businessConfig.getInt(BusinessConfigKey.TRACKER_OFFPLAN_NAME_RADIUS_M);
        Set<UUID> matched = new HashSet<>();
        List<UnnamedStop> unnamed = new ArrayList<>();
        for (TrackStayDetector.Stay stay : stays) {
            TrackVisit match = null;
            for (TrackVisit visit : existing) {
                if (!matched.contains(visit.getId()) && TrackStayDetector.isSameStop(
                        stay.latitude(), stay.longitude(), stay.arrivedMs(), stay.departedMs(),
                        visit.getLatitude().doubleValue(), visit.getLongitude().doubleValue(),
                        epochMillis(visit.getArrivedAt()), epochMillisOrNull(visit.getDepartedAt()), radiusM)) {
                    match = visit;
                    break;
                }
            }
            TrackVisit visit = match != null ? match : TrackVisit.builder()
                    .id(UUID.randomUUID())
                    .tripId(trip.getId())
                    .userId(userId)
                    .localDate(date)
                    .kind(TrackVisitKind.OFF_PLAN)
                    .status(TrackVisitStatus.SUGGESTED)
                    .build();
            visit.setLatitude(coordinate(stay.latitude()));
            visit.setLongitude(coordinate(stay.longitude()));
            visit.setArrivedAt(utc(stay.arrivedMs()));
            visit.setDepartedAt(stay.departedMs() == null ? null : utc(stay.departedMs()));
            if (visit.getName() == null && visit.getStatus() == TrackVisitStatus.SUGGESTED) {
                stopNamer.nearestCatalogPlace(stay.latitude(), stay.longitude(), nameRadiusM).ifPresentOrElse(
                        place -> {
                            visit.setName(place.name());
                            visit.setPlaceId(place.placeId());
                        },
                        () -> unnamed.add(new UnnamedStop(visit.getId(), stay.latitude(), stay.longitude())));
            }
            if (match != null) {
                matched.add(match.getId());
                trackRepository.updateOffPlanStop(visit);
            } else {
                trackRepository.insertVisit(visit);
            }
        }
        for (TrackVisit visit : existing) {
            if (!matched.contains(visit.getId()) && visit.getStatus() == TrackVisitStatus.SUGGESTED) {
                trackRepository.deleteVisit(visit.getId(), userId);
            }
        }
        return unnamed;
    }

    /**
     * Asks Goong for the names the catalogue could not give, after commit and off the request
     * thread. Each answer only fills a name that is still empty; a failure leaves the stop
     * unnamed (the app shows "Điểm dừng"), never an error.
     */
    private void nameStopsAfterCommit(UUID tripId, UUID userId, List<UnnamedStop> unnamed) {
        if (unnamed.isEmpty()) {
            return;
        }
        List<UnnamedStop> batch = List.copyOf(unnamed.subList(0, Math.min(unnamed.size(), MAX_GEOCODES_PER_CHANGE)));
        runAfterCommit(() -> stopNamer.submit(() -> nameStops(tripId, userId, batch)));
    }

    void nameStops(UUID tripId, UUID userId, List<UnnamedStop> stops) {
        boolean isChanged = false;
        for (UnnamedStop stop : stops) {
            try {
                String name = stopNamer.reverseGeocode(userId, stop.latitude(), stop.longitude());
                if (name != null && trackRepository.updateOffPlanNameIfMissing(stop.visitId(), userId, name) == 1) {
                    isChanged = true;
                }
            } catch (RuntimeException exception) {
                log.warn("Naming off-plan stop {} failed: {}", stop.visitId(), exception.getMessage());
            }
        }
        if (isChanged) {
            publishUpdated(tripId, userId, List.of());
        }
    }

    private static void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    // --- off-plan stop actions (P2) ------------------------------------------------------

    @Override
    @Transactional
    public TrackResponses.Visit dismissVisit(UUID userId, UUID tripId, UUID visitId) {
        tripAccessGuard.requireAccess(tripId, userId);
        requireEnabled(userId);
        TrackVisit visit = requireOffPlanVisit(tripId, userId, visitId);
        trackRepository.updateOffPlanStatus(visitId, userId, TrackVisitStatus.DISMISSED, visit.getCreatedActivityId());
        visit.setStatus(TrackVisitStatus.DISMISSED);
        publishUpdated(tripId, userId, List.of(visit.getLocalDate()));
        return toResponse(visit);
    }

    @Override
    @Transactional
    public AddOutcome addVisitToItinerary(UUID userId, UUID tripId, UUID visitId,
                                          AddTrackVisitToItineraryRequest request) {
        // Only the stop's owner, and only if they may change the itinerary.
        Trip trip = tripAccessGuard.requireEditAccess(tripId, userId);
        requireEnabled(userId);
        TrackVisit visit = requireOffPlanVisit(tripId, userId, visitId);
        if (visit.getCreatedActivityId() != null) {
            if (visit.getStatus() != TrackVisitStatus.ADDED) {
                trackRepository.updateOffPlanStatus(visitId, userId, TrackVisitStatus.ADDED,
                        visit.getCreatedActivityId());
                visit.setStatus(TrackVisitStatus.ADDED);
            }
            return new AddOutcome(new TrackResponses.AddedToItinerary(toResponse(visit), visit.getCreatedActivityId()),
                    true);
        }
        int dayNumber = dayNumberOf(trip, visit.getLocalDate());
        if (dayNumber < 1 || dayNumber > dayCountOf(trip)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "This stop is outside the trip's days");
        }
        AddTrackVisitToItineraryRequest body = request == null ? new AddTrackVisitToItineraryRequest() : request;
        String name = body.getName() != null && !body.getName().isBlank() ? body.getName().trim() : visit.getName();
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "name is required for an unnamed stop");
        }
        LocalTime startTime = body.getStartTime() != null
                ? body.getStartTime()
                : roundedLocalTime(visit.getArrivedAt(), zoneOf(trip));
        ActivityResponse activity = activityService.createActivity(tripId, CreateActivityRequest.builder()
                .name(name)
                .lat(visit.getLatitude())
                .lng(visit.getLongitude())
                .dayNumber(dayNumber)
                .startTime(startTime)
                .category(body.getCategory() == null || body.getCategory().isBlank() ? null : body.getCategory().trim())
                // A catalogue id in placeId is what links the activity to the place (placeRefId).
                .placeId(visit.getPlaceId() == null ? null : visit.getPlaceId().toString())
                .build(), userId);
        trackRepository.updateOffPlanStatus(visitId, userId, TrackVisitStatus.ADDED, activity.getId());
        visit.setStatus(TrackVisitStatus.ADDED);
        visit.setCreatedActivityId(activity.getId());
        publishUpdated(tripId, userId, List.of(visit.getLocalDate()));
        return new AddOutcome(new TrackResponses.AddedToItinerary(toResponse(visit), activity.getId()), false);
    }

    private TrackVisit requireOffPlanVisit(UUID tripId, UUID userId, UUID visitId) {
        TrackVisit visit = trackRepository.findVisit(visitId, tripId, userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.TRACK_VISIT_NOT_FOUND, "Visit not found"));
        if (visit.getKind() != TrackVisitKind.OFF_PLAN) {
            throw new BusinessException(ErrorConstant.TRACK_VISIT_NOT_OFF_PLAN,
                    "Only an off-plan stop can be dismissed or added");
        }
        return visit;
    }

    /** Trip-local time of a UTC instant, snapped to the nearest five minutes within the same day. */
    static LocalTime roundedLocalTime(LocalDateTime utc, ZoneId zone) {
        LocalTime local = utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalTime();
        int minutes = local.getHour() * 60 + local.getMinute();
        int rounded = Math.round(minutes / (float) START_TIME_STEP_MINUTES) * START_TIME_STEP_MINUTES;
        int lastSlot = 24 * 60 - START_TIME_STEP_MINUTES;
        rounded = Math.min(rounded, lastSlot);
        return LocalTime.of(rounded / 60, rounded % 60);
    }

    // --- reads --------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<TrackResponses.Day> days(UUID viewerId, UUID tripId, UUID ownerId, Integer dayNumber) {
        Trip trip = tripAccessGuard.requireAccess(tripId, viewerId);
        requireEnabled(viewerId);
        UUID owner = ownerId == null ? viewerId : ownerId;
        if (!owner.equals(viewerId)) {
            if (!acceptedMemberIds(trip).contains(owner)) {
                throw new BusinessException(ErrorConstant.NOT_FOUND, "Not a member of this trip");
            }
            TripTrackSettings ownerSettings = trackRepository.findSettings(tripId, owner).orElse(null);
            if (!isVisibleToMembers(ownerSettings)) {
                throw new BusinessException(ErrorConstant.TRACK_NOT_VISIBLE, "This member keeps their route private");
            }
        }
        return toDayResponses(trip, viewerId, List.of(owner), dayNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrackResponses.Day> overview(UUID viewerId, UUID tripId, Integer dayNumber) {
        Trip trip = tripAccessGuard.requireAccess(tripId, viewerId);
        requireEnabled(viewerId);
        Set<UUID> members = new HashSet<>(acceptedMemberIds(trip));
        Map<UUID, TripTrackSettings> settingsByUser = settingsByUser(tripId);
        List<UUID> visible = new ArrayList<>();
        for (UUID userId : trackRepository.findUserIdsWithDays(tripId, MEMBER_LIMIT)) {
            if (!members.contains(userId)) {
                continue;
            }
            if (userId.equals(viewerId) || isVisibleToMembers(settingsByUser.get(userId))) {
                visible.add(userId);
            }
        }
        return toDayResponses(trip, viewerId, visible, dayNumber);
    }

    private List<TrackResponses.Day> toDayResponses(Trip trip, UUID viewerId, List<UUID> owners, Integer dayNumber) {
        UUID tripId = trip.getId();
        int padDays = businessConfig.getInt(BusinessConfigKey.TRACKER_WINDOW_PAD_DAYS);
        List<TrackDay> days = trackRepository.findDays(tripId, owners, dayNumber, DAY_LIMIT);
        if (days.isEmpty()) {
            return List.of();
        }
        List<LocalDate> dates = days.stream().map(TrackDay::getLocalDate).distinct().toList();
        Map<String, List<TrackVisit>> visitsByDay = new HashMap<>();
        for (TrackVisit visit : trackRepository.findVisits(tripId, owners, dates, VISIT_LIMIT)) {
            visitsByDay.computeIfAbsent(dayKey(visit.getUserId(), visit.getLocalDate()), ignored -> new ArrayList<>())
                    .add(visit);
        }
        List<UUID> others = owners.stream().filter(owner -> !owner.equals(viewerId)).toList();
        Map<UUID, List<PrivacyZoneClipper.Zone>> zonesByUser = new HashMap<>();
        for (PrivacyZone zone : trackRepository.findZonesForUsers(others, ZONE_LIMIT)) {
            zonesByUser.computeIfAbsent(zone.getUserId(), ignored -> new ArrayList<>()).add(toClipZone(zone));
        }

        List<TrackResponses.Day> responses = new ArrayList<>();
        for (TrackDay day : days) {
            List<PrivacyZoneClipper.Zone> zones = day.getUserId().equals(viewerId)
                    ? List.of()
                    : zonesByUser.getOrDefault(day.getUserId(), List.of());
            List<TrackSegmentLine> stored = readSegments(day.getLineSegments());
            PrivacyZoneClipper.Clipped clipped = PrivacyZoneClipper.clip(stored, zones);
            List<TrackResponses.Visit> visits = new ArrayList<>();
            for (TrackVisit visit : visitsByDay.getOrDefault(dayKey(day.getUserId(), day.getLocalDate()), List.of())) {
                if (!zones.isEmpty() && PrivacyZoneClipper.isHidden(
                        visit.getLatitude().doubleValue(), visit.getLongitude().doubleValue(), zones)) {
                    continue;
                }
                visits.add(toResponse(visit));
            }
            responses.add(new TrackResponses.Day(
                    day.getUserId(),
                    day.getLocalDate(),
                    nullToZero(day.getDayNumber()),
                    toSummary(day),
                    toSegments(clipped.segments()),
                    clipped.bbox(),
                    visits,
                    // The trip's dates may have moved since this day was recorded (TR-05).
                    !inWindow(trip, day.getLocalDate(), padDays),
                    readProvinceCodes(day.getProvinceCodes())));
        }
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public TrackResponses.DayPoints dayPoints(UUID userId, UUID tripId, LocalDate date) {
        tripAccessGuard.requireAccess(tripId, userId);
        requireEnabled(userId);
        List<TrackPoint> points = new ArrayList<>();
        for (TrackChunk chunk : trackRepository.findChunksForDay(tripId, userId, date, DAY_CHUNK_LIMIT)) {
            points.addAll(readChunk(chunk));
        }
        points.sort(Comparator.comparingLong(TrackPoint::timeMs));
        List<TrackPoint> unique = new ArrayList<>(points.size());
        for (TrackPoint point : points) {
            if (unique.isEmpty() || unique.get(unique.size() - 1).timeMs() != point.timeMs()) {
                unique.add(point);
            }
        }
        return new TrackResponses.DayPoints(date, TrackPointsCodec.encode(downsample(unique, MAX_DAY_POINTS)));
    }

    // --- deletion -----------------------------------------------------------------------

    @Override
    @Transactional
    public TrackResponses.Deletion delete(UUID userId, UUID tripId, TrackDeletionRequest request) {
        // No feature-flag check: a person can always remove what they recorded.
        Trip trip = tripAccessGuard.requireAccess(tripId, userId);
        return switch (request.getScope()) {
            case TRIP -> {
                List<LocalDate> dates = trackRepository.findDayDates(tripId, userId);
                trackRepository.deleteTripMemberTracks(tripId, userId);
                publishUpdated(tripId, userId, dates);
                yield new TrackResponses.Deletion(List.of(), dates);
            }
            case DAY -> {
                LocalDate date = requireDate(request);
                trackRepository.deleteDayCompletely(tripId, userId, date);
                publishUpdated(tripId, userId, List.of(date));
                yield new TrackResponses.Deletion(List.of(), List.of(date));
            }
            case RANGES -> deleteRanges(trip, userId, requireDate(request), request.getRanges());
        };
    }

    private TrackResponses.Deletion deleteRanges(Trip trip, UUID userId, LocalDate date,
                                                 List<TrackDeletionRequest.Range> ranges) {
        if (ranges == null || ranges.isEmpty()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "ranges are required");
        }
        for (TrackDeletionRequest.Range range : ranges) {
            if (range.getFromMs() > range.getToMs()) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "fromMs must not be after toMs");
            }
        }
        for (TrackChunk chunk : trackRepository.findChunksForDay(trip.getId(), userId, date, DAY_CHUNK_LIMIT)) {
            List<TrackPoint> points = readChunk(chunk);
            List<TrackPoint> remaining = points.stream()
                    .filter(point -> ranges.stream().noneMatch(
                            range -> point.timeMs() >= range.getFromMs() && point.timeMs() <= range.getToMs()))
                    .toList();
            if (remaining.size() == points.size()) {
                continue;
            }
            if (remaining.isEmpty()) {
                trackRepository.deleteChunk(chunk.getId(), userId);
                continue;
            }
            chunk.setPoints(writeJson(TrackPointsCodec.encode(remaining)));
            chunk.setPointCount(remaining.size());
            chunk.setStartedAt(utc(remaining.get(0).timeMs()));
            chunk.setEndedAt(utc(remaining.get(remaining.size() - 1).timeMs()));
            trackRepository.updateChunkPoints(chunk);
        }
        List<UnnamedStop> unnamed = new ArrayList<>();
        TrackDay day = rebuildDay(trip, userId, date, trackerSettings(), unnamed);
        publishUpdated(trip.getId(), userId, List.of(date));
        nameStopsAfterCommit(trip.getId(), userId, unnamed);
        return day == null
                ? new TrackResponses.Deletion(List.of(), List.of(date))
                : new TrackResponses.Deletion(List.of(toSummary(day)), List.of());
    }

    // --- settings -----------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public TrackResponses.Settings settings(UUID userId, UUID tripId) {
        tripAccessGuard.requireAccess(tripId, userId);
        return toResponse(trackRepository.findSettings(tripId, userId)
                .orElse(TripTrackSettings.defaults(tripId, userId)));
    }

    @Override
    @Transactional
    public TrackResponses.Settings updateSettings(UUID userId, UUID tripId, UpdateTrackSettingsRequest request) {
        Trip trip = tripAccessGuard.requireAccess(tripId, userId);
        if (request.getVisibility() == TrackVisibility.PUBLIC_VIEWERS
                && trip.getVisibility() != TripVisibility.SHARED
                && trip.getVisibility() != TripVisibility.PUBLIC) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "Public viewers can only be chosen for a shared or public trip");
        }
        TripTrackSettings existing = trackRepository.findSettings(tripId, userId).orElse(null);
        long expected = request.getExpectedVersion();
        if (existing == null) {
            if (expected != 0) {
                throw conflict();
            }
            existing = TripTrackSettings.builder()
                    .id(UUID.randomUUID())
                    .tripId(tripId)
                    .userId(userId)
                    .autoEnabled(false)
                    .build();
            applySettings(existing, request);
            trackRepository.insertSettings(existing);
        } else {
            applySettings(existing, request);
            if (trackRepository.updateSettings(existing, expected) != 1) {
                throw conflict();
            }
        }
        // Visibility decides what the other members' maps show; let them refresh.
        publishUpdated(tripId, userId, List.of());
        return toResponse(trackRepository.findSettings(tripId, userId).orElseThrow());
    }

    private static void applySettings(TripTrackSettings settings, UpdateTrackSettingsRequest request) {
        settings.setVisibility(request.getVisibility());
        settings.setTrimDayEnds(request.getTrimDayEnds());
        settings.setDefaultAccuracy(request.getDefaultAccuracy());
        if (request.getAutoEnabled() != null) {
            settings.setAutoEnabled(request.getAutoEnabled());
        }
    }

    // --- privacy zones ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<TrackResponses.Zone> privacyZones(UUID userId) {
        return trackRepository.findZones(userId, ZONE_LIMIT).stream().map(TrackServiceImpl::toResponse).toList();
    }

    @Override
    @Transactional
    public TrackResponses.Zone createPrivacyZone(UUID userId, PrivacyZoneRequest request) {
        if (trackRepository.countZones(userId) >= MAX_ZONES) {
            throw new BusinessException(ErrorConstant.TRACK_PRIVACY_ZONE_LIMIT,
                    "At most " + MAX_ZONES + " privacy zones");
        }
        PrivacyZone zone = PrivacyZone.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name(request.getName().trim())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .radiusM(request.getRadiusM())
                .build();
        trackRepository.insertZone(zone);
        return findZone(userId, zone.getId());
    }

    @Override
    @Transactional
    public TrackResponses.Zone updatePrivacyZone(UUID userId, UUID zoneId, PrivacyZoneRequest request) {
        if (request.getExpectedVersion() == null) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "expectedVersion is required");
        }
        findZone(userId, zoneId);
        PrivacyZone zone = PrivacyZone.builder()
                .id(zoneId)
                .userId(userId)
                .name(request.getName().trim())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .radiusM(request.getRadiusM())
                .build();
        if (trackRepository.updateZone(zone, request.getExpectedVersion()) != 1) {
            throw conflict();
        }
        return findZone(userId, zoneId);
    }

    @Override
    @Transactional
    public void deletePrivacyZone(UUID userId, UUID zoneId) {
        if (trackRepository.deleteZone(zoneId, userId) != 1) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Privacy zone not found");
        }
    }

    private TrackResponses.Zone findZone(UUID userId, UUID zoneId) {
        return trackRepository.findZones(userId, ZONE_LIMIT).stream()
                .filter(zone -> zone.getId().equals(zoneId))
                .findFirst()
                .map(TrackServiceImpl::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Privacy zone not found"));
    }

    // --- whole-account and lifecycle ----------------------------------------------------

    @Override
    @Transactional
    public void deleteAllTracks(UUID userId) {
        trackRepository.purgeUserTracks(userId);
    }

    @Override
    @Transactional
    public void onUserDeleted(UUID userId) {
        trackRepository.purgeUser(userId);
    }

    @Override
    @Transactional
    public void onTripDeleted(UUID tripId) {
        trackRepository.purgeTrip(tripId);
    }

    @Override
    @Transactional
    public void onMemberLeft(UUID tripId, UUID userId) {
        List<LocalDate> dates = trackRepository.findDayDates(tripId, userId);
        trackRepository.purgeTripMember(tripId, userId);
        if (!dates.isEmpty()) {
            publishUpdated(tripId, userId, dates);
        }
    }

    @Override
    @Transactional
    public int purgeExpiredChunks(int limit) {
        int retentionDays = businessConfig.getInt(BusinessConfigKey.TRACKER_RAW_RETENTION_DAYS);
        LocalDate lastTripEndDate = LocalDate.now(clock.withZone(DEFAULT_TRIP_ZONE)).minusDays(retentionDays);
        return trackRepository.deleteExpiredChunks(lastTripEndDate, limit);
    }

    // --- helpers ------------------------------------------------------------------------

    private boolean isEnabled(UUID userId) {
        return isEnabled(businessConfig, betaAccess, userId);
    }

    static boolean isEnabled(BusinessConfigService businessConfig, BetaAccessService betaAccess, UUID userId) {
        return businessConfig.getBoolean(BusinessConfigKey.TRACKER_ENABLED) || betaAccess.isBetaUser(userId);
    }

    private void requireEnabled(UUID userId) {
        if (!isEnabled(userId)) {
            throw new BusinessException(ErrorConstant.TRACK_DISABLED, "The travel tracker is not available");
        }
    }

    TrackerSettings trackerSettings() {
        return trackerSettings(businessConfig);
    }

    static TrackerSettings trackerSettings(BusinessConfigService businessConfig) {
        Map<TrackAccuracyMode, Integer> maxAccuracy = new EnumMap<>(TrackAccuracyMode.class);
        maxAccuracy.put(TrackAccuracyMode.HIGH, businessConfig.getInt(BusinessConfigKey.TRACKER_MAX_ACCURACY_HIGH_M));
        maxAccuracy.put(TrackAccuracyMode.BALANCED,
                businessConfig.getInt(BusinessConfigKey.TRACKER_MAX_ACCURACY_BALANCED_M));
        maxAccuracy.put(TrackAccuracyMode.SAVER, businessConfig.getInt(BusinessConfigKey.TRACKER_MAX_ACCURACY_SAVER_M));
        return new TrackerSettings(
                maxAccuracy,
                businessConfig.getInt(BusinessConfigKey.TRACKER_MAX_SPEED_KMH),
                businessConfig.getInt(BusinessConfigKey.TRACKER_GAP_MINUTES),
                businessConfig.getInt(BusinessConfigKey.TRACKER_GAP_DISTANCE_M),
                businessConfig.getInt(BusinessConfigKey.TRACKER_FLIGHT_MIN_KM),
                businessConfig.getInt(BusinessConfigKey.TRACKER_FLIGHT_MIN_KMH),
                businessConfig.getInt(BusinessConfigKey.TRACKER_ARRIVAL_RADIUS_M),
                businessConfig.getInt(BusinessConfigKey.TRACKER_ARRIVAL_MINUTES));
    }

    private TrackResponses.Config config() {
        Map<TrackAccuracyMode, Integer> maxAccuracy = trackerSettings().maxAccuracyM();
        Map<TrackAccuracyMode, Integer> distanceFilter = new EnumMap<>(TrackAccuracyMode.class);
        distanceFilter.put(TrackAccuracyMode.HIGH, businessConfig.getInt(BusinessConfigKey.TRACKER_DISTANCE_FILTER_HIGH_M));
        distanceFilter.put(TrackAccuracyMode.BALANCED,
                businessConfig.getInt(BusinessConfigKey.TRACKER_DISTANCE_FILTER_BALANCED_M));
        distanceFilter.put(TrackAccuracyMode.SAVER,
                businessConfig.getInt(BusinessConfigKey.TRACKER_DISTANCE_FILTER_SAVER_M));
        return TrackResponses.Config.builder()
                .maxAccuracyM(maxAccuracy)
                .distanceFilterM(distanceFilter)
                .maxSpeedKmh(businessConfig.getInt(BusinessConfigKey.TRACKER_MAX_SPEED_KMH))
                .stationaryRadiusM(businessConfig.getInt(BusinessConfigKey.TRACKER_STATIONARY_RADIUS_M))
                .stationaryMinutes(businessConfig.getInt(BusinessConfigKey.TRACKER_STATIONARY_MINUTES))
                .arrivalRadiusM(businessConfig.getInt(BusinessConfigKey.TRACKER_ARRIVAL_RADIUS_M))
                .arrivalMinutes(businessConfig.getInt(BusinessConfigKey.TRACKER_ARRIVAL_MINUTES))
                .nudgeCooldownMinutes(businessConfig.getInt(BusinessConfigKey.TRACKER_NUDGE_COOLDOWN_MINUTES))
                .nudgeDailyMax(businessConfig.getInt(BusinessConfigKey.TRACKER_NUDGE_DAILY_MAX))
                .quietStartHour(businessConfig.getInt(BusinessConfigKey.TRACKER_QUIET_START_HOUR))
                .quietEndHour(businessConfig.getInt(BusinessConfigKey.TRACKER_QUIET_END_HOUR))
                .lowBatteryPct(businessConfig.getInt(BusinessConfigKey.TRACKER_LOW_BATTERY_PCT))
                .stopBatteryPct(businessConfig.getInt(BusinessConfigKey.TRACKER_STOP_BATTERY_PCT))
                .chunkMaxPoints(businessConfig.getInt(BusinessConfigKey.TRACKER_CHUNK_MAX_POINTS))
                .checkinMaxAccuracyM(businessConfig.getInt(BusinessConfigKey.CHECKIN_MAX_ACCURACY_METERS))
                .offPlanRadiusM(businessConfig.getInt(BusinessConfigKey.TRACKER_OFFPLAN_RADIUS_M))
                .offPlanMinMinutes(businessConfig.getInt(BusinessConfigKey.TRACKER_OFFPLAN_MIN_MINUTES))
                .offPlanNameRadiusM(businessConfig.getInt(BusinessConfigKey.TRACKER_OFFPLAN_NAME_RADIUS_M))
                .build();
    }

    /** Owner first, then accepted members; guests have no account and never record. */
    private List<UUID> acceptedMemberIds(Trip trip) {
        return acceptedMemberIds(tripMemberRepository, trip);
    }

    static List<UUID> acceptedMemberIds(TripMemberRepository tripMemberRepository, Trip trip) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (trip.getOwnerId() != null) {
            ids.add(trip.getOwnerId());
        }
        for (TripMember member : tripMemberRepository.findByTripId(trip.getId())) {
            if (member.getUserId() != null && member.getStatus() == MemberStatus.ACCEPTED) {
                ids.add(member.getUserId());
            }
        }
        return new ArrayList<>(ids);
    }

    private Map<UUID, TripTrackSettings> settingsByUser(UUID tripId) {
        Map<UUID, TripTrackSettings> byUser = new HashMap<>();
        for (TripTrackSettings settings : trackRepository.findSettingsForTrip(tripId, MEMBER_LIMIT)) {
            byUser.put(settings.getUserId(), settings);
        }
        return byUser;
    }

    /** No settings row means the default, which shares with members. */
    static boolean isVisibleToMembers(TripTrackSettings settings) {
        TrackVisibility visibility = settings == null ? TripTrackSettings.DEFAULT_VISIBILITY : settings.getVisibility();
        return visibility != TrackVisibility.PRIVATE;
    }

    static ZoneId zoneOf(Trip trip) {
        if (trip.getTimezone() == null || trip.getTimezone().isBlank()) {
            return DEFAULT_TRIP_ZONE;
        }
        try {
            return ZoneId.of(trip.getTimezone());
        } catch (DateTimeException exception) {
            return DEFAULT_TRIP_ZONE;
        }
    }

    static boolean inWindow(Trip trip, LocalDate date, int padDays) {
        return !date.isBefore(trip.getStartDate().minusDays(padDays))
                && !date.isAfter(trip.getEndDate().plusDays(padDays));
    }

    static int dayNumberOf(Trip trip, LocalDate date) {
        return (int) ChronoUnit.DAYS.between(trip.getStartDate(), date) + 1;
    }

    static int dayCountOf(Trip trip) {
        return (int) ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
    }

    private static boolean isPlausibleCoordinate(TrackPoint point) {
        return Math.abs(point.latitude()) <= 90 && Math.abs(point.longitude()) <= 180
                && !(point.latitude() == 0 && point.longitude() == 0);
    }

    private static List<TrackPoint> decode(TrackPointsPayload payload, int maxPoints) {
        try {
            return TrackPointsCodec.decode(payload, maxPoints);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorConstant.TRACK_CHUNK_INVALID, exception.getMessage());
        }
    }

    private List<TrackPoint> readChunk(TrackChunk chunk) {
        return readChunk(objectMapper, chunk);
    }

    static List<TrackPoint> readChunk(ObjectMapper objectMapper, TrackChunk chunk) {
        try {
            return TrackPointsCodec.decode(
                    objectMapper.readValue(chunk.getPoints(), TrackPointsPayload.class), STORED_CHUNK_MAX_POINTS);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            // A stored chunk that no longer decodes is skipped, not allowed to break the day.
            log.error("Unreadable track chunk {}: {}", chunk.getId(), exception.getMessage());
            return List.of();
        }
    }

    private List<TrackSegmentLine> readSegments(String json) {
        return readSegments(objectMapper, json);
    }

    static List<TrackSegmentLine> readSegments(ObjectMapper objectMapper, String json) {
        try {
            List<TrackResponses.Segment> segments = objectMapper.readValue(json, SEGMENTS_TYPE);
            return segments.stream().map(segment -> new TrackSegmentLine(segment.kind(), segment.polyline())).toList();
        } catch (JsonProcessingException exception) {
            log.error("Unreadable track day segments: {}", exception.getMessage());
            return List.of();
        }
    }

    private List<String> readProvinceCodes(String json) {
        return readProvinceCodes(objectMapper, json);
    }

    static List<String> readProvinceCodes(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, PROVINCE_CODES_TYPE);
        } catch (JsonProcessingException exception) {
            log.error("Unreadable track day province codes: {}", exception.getMessage());
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialise track data", exception);
        }
    }

    static List<TrackResponses.Segment> toSegments(List<TrackSegmentLine> lines) {
        return lines.stream().map(line -> new TrackResponses.Segment(line.kind(), line.polyline())).toList();
    }

    /** Keeps every n-th point so the owner's editor gets at most {@code max}; ends are kept. */
    static List<TrackPoint> downsample(List<TrackPoint> points, int max) {
        if (points.size() <= max) {
            return points;
        }
        List<TrackPoint> sampled = new ArrayList<>(max);
        double step = (points.size() - 1) / (double) (max - 1);
        for (int i = 0; i < max; i++) {
            sampled.add(points.get((int) Math.round(i * step)));
        }
        return sampled;
    }

    private void publishUpdated(UUID tripId, UUID userId, Collection<LocalDate> dates) {
        List<String> values = dates.stream().map(LocalDate::toString).toList();
        realtimePublisher.publishAfterCommit(TripRealtimeEventType.TRACK_UPDATED, tripId, userId, userId,
                Map.of("dates", values));
    }

    private static LocalDate requireDate(TrackDeletionRequest request) {
        if (request.getDate() == null) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "date is required");
        }
        return request.getDate();
    }

    private static BusinessException conflict() {
        return new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                "Changed on another device; reload and try again");
    }

    static long epochMillis(LocalDateTime utc) {
        return utc.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static Long epochMillisOrNull(LocalDateTime utc) {
        return utc == null ? null : epochMillis(utc);
    }

    /** NUMERIC(10, 7), about 1 cm. */
    private static BigDecimal coordinate(double degrees) {
        return BigDecimal.valueOf(degrees).setScale(7, RoundingMode.HALF_UP);
    }

    static LocalDateTime utc(Long epochMillis) {
        return epochMillis == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
    }

    private static String dayKey(UUID userId, LocalDate date) {
        return userId + "|" + date;
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    static String displayName(User user) {
        return user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getUsername();
    }

    static PrivacyZoneClipper.Zone toClipZone(PrivacyZone zone) {
        return new PrivacyZoneClipper.Zone(
                zone.getLatitude().doubleValue(), zone.getLongitude().doubleValue(), zone.getRadiusM());
    }

    private static TrackResponses.Settings toResponse(TripTrackSettings settings) {
        return new TrackResponses.Settings(
                settings.getVisibility(),
                Boolean.TRUE.equals(settings.getTrimDayEnds()),
                settings.getDefaultAccuracy(),
                Boolean.TRUE.equals(settings.getAutoEnabled()),
                settings.getDataVersion() == null ? 0 : settings.getDataVersion());
    }

    private static TrackResponses.DaySummary toSummary(TrackDay day) {
        return TrackResponses.DaySummary.builder()
                .date(day.getLocalDate())
                .dayNumber(nullToZero(day.getDayNumber()))
                .distanceM(nullToZero(day.getDistanceM()))
                .flightDistanceM(nullToZero(day.getFlightDistanceM()))
                .movingSeconds(nullToZero(day.getMovingSeconds()))
                .elevationGainM(nullToZero(day.getElevationGainM()))
                .elevationLossM(nullToZero(day.getElevationLossM()))
                .pointCount(nullToZero(day.getPointCount()))
                .firstAt(day.getFirstAt())
                .lastAt(day.getLastAt())
                .build();
    }

    private static TrackResponses.Visit toResponse(TrackVisit visit) {
        return new TrackResponses.Visit(
                visit.getId(),
                visit.getKind(),
                visit.getActivityId(),
                visit.getName(),
                visit.getLatitude(),
                visit.getLongitude(),
                visit.getArrivedAt(),
                visit.getDepartedAt(),
                visit.getStatus(),
                visit.getPlaceId(),
                visit.getCreatedActivityId());
    }

    private static TrackResponses.Zone toResponse(PrivacyZone zone) {
        return new TrackResponses.Zone(
                zone.getId(),
                zone.getName(),
                zone.getLatitude(),
                zone.getLongitude(),
                nullToZero(zone.getRadiusM()),
                zone.getDataVersion() == null ? 0 : zone.getDataVersion());
    }
}
