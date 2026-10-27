package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.TrackResponses;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.PrivacyZone;
import com.ds.goroute.entity.TrackChunk;
import com.ds.goroute.entity.TrackDay;
import com.ds.goroute.entity.TrackProvince;
import com.ds.goroute.entity.TrackUserAggregate;
import com.ds.goroute.entity.TrackUserTotals;
import com.ds.goroute.entity.TrackVisit;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripTrackSettings;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.TrackRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.TrackSharingService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.track.GpxWriter;
import com.ds.goroute.service.track.PolylineCodec;
import com.ds.goroute.service.track.PrivacyZoneClipper;
import com.ds.goroute.service.track.TrackDayBuilder;
import com.ds.goroute.service.track.TrackDayResult;
import com.ds.goroute.service.track.TrackDayTrimmer;
import com.ds.goroute.service.track.TrackPoint;
import com.ds.goroute.service.track.TrackRouteCoarsener;
import com.ds.goroute.service.track.TrackSegmentLine;
import com.ds.goroute.service.track.TrackerSettings;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.TrackSegmentKind;
import com.ds.goroute.type.TrackVisibility;
import com.ds.goroute.type.TrackVisitKind;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Travel tracker, phase 4 (TRAVEL_TRACKER_API_CONTRACT.md v1.1, section D). Everything here
 * reads what {@link TrackServiceImpl} stored; nothing writes.
 *
 * <p>The public route follows TR-21..TR-23: only members who chose PUBLIC_VIEWERS, never a
 * raw point or a timestamp, privacy zones always removed, the first and last 300 m of each
 * day removed when the member kept that option, and today drawn only up to the member's last
 * check-in so nobody can tell where they are right now.
 */
@Service
@RequiredArgsConstructor
public class TrackSharingServiceImpl implements TrackSharingService {

    /** Day rows (member x day) the public page may carry. */
    static final int PUBLIC_DAY_LIMIT = 60;
    /** Metres cut from both ends of a day for outsiders when {@code trimDayEnds} is on (TR-23). */
    static final double PUBLIC_TRIM_M = 300;

    private static final int MEMBER_LIMIT = 200;
    private static final int DAY_LIMIT = 1_000;
    private static final int VISIT_LIMIT = 5_000;
    private static final int ZONE_LIMIT = 100;
    private static final int TRIP_ID_PREFIX = 8;
    /** Trips on the caller's overview map, and the day rows read to draw them. */
    static final int MY_ROUTES_TRIP_LIMIT = 100;
    private static final int MY_ROUTES_DAY_LIMIT = 3_000;
    /** Points one trip's walked line keeps on the overview map. */
    static final int MY_ROUTE_POINTS = 400;

    private final TrackRepository trackRepository;
    private final TripRepository tripRepository;
    private final TripAccessGuard tripAccessGuard;
    private final TripMemberRepository tripMemberRepository;
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final BusinessConfigService businessConfig;
    private final BetaAccessService betaAccess;
    private final ObjectMapper objectMapper;

    /** Replaced in tests. */
    Clock clock = Clock.systemUTC();

    // --- public page ----------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<TrackResponses.PublicDay> publicTrack(UUID viewerId, UUID tripId) {
        Trip trip = tripAccessGuard.requirePublicView(tripId, viewerId);
        Map<UUID, TripTrackSettings> settingsByUser = new HashMap<>();
        for (TripTrackSettings settings : trackRepository.findSettingsForTrip(tripId, MEMBER_LIMIT)) {
            settingsByUser.put(settings.getUserId(), settings);
        }
        List<UUID> sharing = new ArrayList<>();
        for (UUID memberId : TrackServiceImpl.acceptedMemberIds(tripMemberRepository, trip)) {
            TripTrackSettings settings = settingsByUser.get(memberId);
            // No settings row means MEMBERS; only an explicit choice reaches the public.
            if (settings != null && settings.getVisibility() == TrackVisibility.PUBLIC_VIEWERS
                    && TrackServiceImpl.isEnabled(businessConfig, betaAccess, memberId)) {
                sharing.add(memberId);
            }
        }
        if (sharing.isEmpty()) {
            return List.of();
        }
        List<TrackDay> days = trackRepository.findDays(tripId, sharing, null, PUBLIC_DAY_LIMIT);
        if (days.isEmpty()) {
            return List.of();
        }

        ZoneId zone = TrackServiceImpl.zoneOf(trip);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Map<UUID, LocalDateTime> lastCheckinToday = latestCheckinsToday(trip, days, today, zone);
        Map<UUID, List<PrivacyZoneClipper.Zone>> zonesByUser = zonesByUser(sharing);
        Map<UUID, User> users = new HashMap<>();
        for (User user : userRepository.findByIds(sharing)) {
            users.put(user.getId(), user);
        }
        TrackerSettings trackerSettings = null;

        List<TrackResponses.PublicDay> result = new ArrayList<>();
        for (TrackDay day : days) {
            if (day.getLocalDate().isAfter(today)) {
                continue;
            }
            List<TrackSegmentLine> lines;
            int distanceM;
            if (day.getLocalDate().equals(today)) {
                LocalDateTime cutoff = lastCheckinToday.get(day.getUserId());
                if (cutoff == null) {
                    continue;
                }
                if (trackerSettings == null) {
                    trackerSettings = TrackServiceImpl.trackerSettings(businessConfig);
                }
                TrackDayResult upToCheckin = TrackDayBuilder.build(
                        pointsUntil(trip, day, TrackServiceImpl.epochMillis(cutoff)), trackerSettings);
                if (upToCheckin.isEmpty()) {
                    continue;
                }
                lines = upToCheckin.segments();
                distanceM = upToCheckin.distanceM();
            } else {
                lines = TrackServiceImpl.readSegments(objectMapper, day.getLineSegments());
                distanceM = day.getDistanceM() == null ? 0 : day.getDistanceM();
            }
            if (Boolean.TRUE.equals(settingsByUser.get(day.getUserId()).getTrimDayEnds())) {
                lines = TrackDayTrimmer.trim(lines, PUBLIC_TRIM_M);
            }
            PrivacyZoneClipper.Clipped clipped =
                    PrivacyZoneClipper.clip(lines, zonesByUser.getOrDefault(day.getUserId(), List.of()));
            if (clipped.segments().isEmpty()) {
                continue;
            }
            User user = users.get(day.getUserId());
            result.add(new TrackResponses.PublicDay(
                    day.getUserId(),
                    user == null ? null : TrackServiceImpl.displayName(user),
                    user == null ? null : user.getAvatarUrl(),
                    day.getLocalDate(),
                    day.getDayNumber() == null ? 0 : day.getDayNumber(),
                    distanceM,
                    TrackServiceImpl.toSegments(clipped.segments()),
                    clipped.bbox()));
        }
        return result;
    }

    private Map<UUID, LocalDateTime> latestCheckinsToday(Trip trip, List<TrackDay> days, LocalDate today,
                                                         ZoneId zone) {
        List<UUID> recordedToday = days.stream()
                .filter(day -> day.getLocalDate().equals(today))
                .map(TrackDay::getUserId)
                .distinct()
                .toList();
        Map<UUID, LocalDateTime> latest = new HashMap<>();
        if (recordedToday.isEmpty()) {
            return latest;
        }
        LocalDateTime fromUtc = LocalDateTime.ofInstant(today.atStartOfDay(zone).toInstant(), ZoneOffset.UTC);
        LocalDateTime toUtc = LocalDateTime.ofInstant(today.plusDays(1).atStartOfDay(zone).toInstant(), ZoneOffset.UTC);
        for (TrackUserAggregate row : trackRepository.findLatestCheckins(trip.getId(), recordedToday, fromUtc, toUtc)) {
            if (row.getLastAt() != null) {
                latest.put(row.getUserId(), row.getLastAt());
            }
        }
        return latest;
    }

    private List<TrackPoint> pointsUntil(Trip trip, TrackDay day, long cutoffMs) {
        List<TrackPoint> points = new ArrayList<>();
        for (TrackChunk chunk : trackRepository.findChunksForDay(trip.getId(), day.getUserId(), day.getLocalDate(),
                TrackServiceImpl.DAY_CHUNK_LIMIT)) {
            for (TrackPoint point : TrackServiceImpl.readChunk(objectMapper, chunk)) {
                if (point.timeMs() <= cutoffMs) {
                    points.add(point);
                }
            }
        }
        return points;
    }

    // --- GPX export ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public GpxExport exportGpx(UUID userId, UUID tripId, LocalDate date) {
        Trip trip = tripAccessGuard.requireAccess(tripId, userId);
        requireEnabled(userId);
        List<TrackDay> days = trackRepository.findDays(tripId, List.of(userId), null, DAY_LIMIT).stream()
                .filter(day -> date == null || day.getLocalDate().equals(date))
                .toList();
        List<List<GpxWriter.Point>> gpxDays = new ArrayList<>();
        for (TrackDay day : days) {
            List<GpxWriter.Point> points = rawGpxPoints(tripId, userId, day.getLocalDate());
            if (points.isEmpty()) {
                points = simplifiedGpxPoints(day);
            }
            if (!points.isEmpty()) {
                gpxDays.add(points);
            }
        }
        if (gpxDays.isEmpty()) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Nothing recorded to export");
        }
        String trackName = (trip.getName() == null ? "Pengoo" : trip.getName())
                + (date == null ? "" : " " + date);
        String fileName = "pengoo-" + tripId.toString().substring(0, TRIP_ID_PREFIX) + "-"
                + (date == null ? "all" : date.toString()) + ".gpx";
        return new GpxExport(fileName, GpxWriter.write(trackName, gpxDays).getBytes(StandardCharsets.UTF_8));
    }

    private List<GpxWriter.Point> rawGpxPoints(UUID tripId, UUID userId, LocalDate date) {
        List<TrackPoint> points = new ArrayList<>();
        for (TrackChunk chunk : trackRepository.findChunksForDay(tripId, userId, date, TrackServiceImpl.DAY_CHUNK_LIMIT)) {
            points.addAll(TrackServiceImpl.readChunk(objectMapper, chunk));
        }
        points.sort(Comparator.comparingLong(TrackPoint::timeMs));
        List<TrackPoint> unique = new ArrayList<>(points.size());
        for (TrackPoint point : points) {
            if (unique.isEmpty() || unique.get(unique.size() - 1).timeMs() != point.timeMs()) {
                unique.add(point);
            }
        }
        List<GpxWriter.Point> result = new ArrayList<>(Math.min(unique.size(), TrackServiceImpl.MAX_DAY_POINTS));
        for (TrackPoint point : TrackServiceImpl.downsample(unique, TrackServiceImpl.MAX_DAY_POINTS)) {
            result.add(new GpxWriter.Point(point.latitude(), point.longitude(), point.altitudeM(), point.timeMs()));
        }
        return result;
    }

    /** Raw points expired (TRACKER/RAW_RETENTION_DAYS): the simplified line, without times. */
    private List<GpxWriter.Point> simplifiedGpxPoints(TrackDay day) {
        List<GpxWriter.Point> result = new ArrayList<>();
        for (TrackSegmentLine segment : TrackServiceImpl.readSegments(objectMapper, day.getLineSegments())) {
            if (segment.kind() != TrackSegmentKind.TRACK) {
                continue;
            }
            for (double[] coordinate : PolylineCodec.decode(segment.polyline())) {
                result.add(new GpxWriter.Point(coordinate[0], coordinate[1], null, null));
            }
        }
        return result;
    }

    // --- recap and lifetime totals -----------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public TrackResponses.Recap recap(UUID viewerId, UUID tripId, UUID ownerId) {
        Trip trip = tripAccessGuard.requireAccess(tripId, viewerId);
        requireEnabled(viewerId);
        UUID owner = ownerId == null ? viewerId : ownerId;
        if (!owner.equals(viewerId)) {
            if (!TrackServiceImpl.acceptedMemberIds(tripMemberRepository, trip).contains(owner)) {
                throw new BusinessException(ErrorConstant.NOT_FOUND, "Not a member of this trip");
            }
            if (!TrackServiceImpl.isVisibleToMembers(trackRepository.findSettings(tripId, owner).orElse(null))) {
                throw new BusinessException(ErrorConstant.TRACK_NOT_VISIBLE, "This member keeps their route private");
            }
        }

        long distance = 0;
        long flightDistance = 0;
        long movingSeconds = 0;
        long elevationGain = 0;
        Set<String> provinceCodes = new TreeSet<>();
        List<TrackDay> days = trackRepository.findDays(tripId, List.of(owner), null, DAY_LIMIT);
        for (TrackDay day : days) {
            distance += nullToZero(day.getDistanceM());
            flightDistance += nullToZero(day.getFlightDistanceM());
            movingSeconds += nullToZero(day.getMovingSeconds());
            elevationGain += nullToZero(day.getElevationGainM());
            provinceCodes.addAll(TrackServiceImpl.readProvinceCodes(objectMapper, day.getProvinceCodes()));
        }

        Set<UUID> planned = new HashSet<>();
        for (Activity activity : activityRepository.findByTripId(tripId)) {
            if (TrackServiceImpl.isPlace(activity)) {
                planned.add(activity.getId());
            }
        }
        Set<UUID> visited = new HashSet<>();
        for (TrackVisit visit : trackRepository.findVisits(tripId, List.of(owner), null, VISIT_LIMIT)) {
            if (visit.getKind() == TrackVisitKind.ACTIVITY && visit.getActivityId() != null) {
                visited.add(visit.getActivityId());
            }
        }
        visited.addAll(trackRepository.findCheckedInActivityIds(tripId, owner));
        visited.retainAll(planned);

        return TrackResponses.Recap.builder()
                .userId(owner)
                .distanceM(distance)
                .flightDistanceM(flightDistance)
                .movingSeconds(movingSeconds)
                .elevationGainM(elevationGain)
                .dayCount(days.size())
                .provincesPassed(toProvinces(trackRepository.findProvinces(List.copyOf(provinceCodes))))
                .visitedActivityCount(visited.size())
                .plannedActivityCount(planned.size())
                .checkinCount(trackRepository.countCheckins(tripId, owner))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TrackResponses.MyStats myStats(UUID userId) {
        requireEnabled(userId);
        TrackUserTotals totals = trackRepository.findUserTotals(userId);
        return new TrackResponses.MyStats(
                totals.getTotalDistanceM() == null ? 0 : totals.getTotalDistanceM(),
                totals.getTripCount() == null ? 0 : totals.getTripCount(),
                toProvinces(trackRepository.findProvincesPassedByUser(userId)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrackResponses.MyTripRoute> myRoutes(UUID userId) {
        requireEnabled(userId);
        List<TrackDay> days = trackRepository.findDaysForUser(userId, MY_ROUTES_DAY_LIMIT);
        if (days.isEmpty()) {
            return List.of();
        }
        // Trips the caller still belongs to; a track outlives neither leaving nor deleting a trip.
        Map<UUID, Trip> trips = new HashMap<>();
        for (Trip trip : tripRepository.findByUserId(userId)) {
            trips.put(trip.getId(), trip);
        }
        Map<UUID, List<TrackDay>> daysByTrip = new LinkedHashMap<>();
        for (TrackDay day : days) {
            if (trips.containsKey(day.getTripId())) {
                daysByTrip.computeIfAbsent(day.getTripId(), ignored -> new ArrayList<>()).add(day);
            }
        }

        List<TrackResponses.MyTripRoute> result = new ArrayList<>();
        for (Map.Entry<UUID, List<TrackDay>> entry : daysByTrip.entrySet()) {
            Trip trip = trips.get(entry.getKey());
            List<TrackSegmentLine> lines = new ArrayList<>();
            Set<String> provinceCodes = new HashSet<>();
            long distance = 0;
            for (TrackDay day : entry.getValue()) {
                lines.addAll(TrackServiceImpl.readSegments(objectMapper, day.getLineSegments()));
                provinceCodes.addAll(TrackServiceImpl.readProvinceCodes(objectMapper, day.getProvinceCodes()));
                distance += nullToZero(day.getDistanceM());
            }
            TrackRouteCoarsener.Coarsened route = TrackRouteCoarsener.coarsen(lines, MY_ROUTE_POINTS);
            if (route.segments().isEmpty()) {
                continue;
            }
            result.add(TrackResponses.MyTripRoute.builder()
                    .tripId(trip.getId())
                    .tripName(trip.getName())
                    .coverImageUrl(trip.getCoverImageUrl())
                    .startDate(trip.getStartDate())
                    .endDate(trip.getEndDate())
                    .distanceM(distance)
                    .dayCount(entry.getValue().size())
                    .provinceCount(provinceCodes.size())
                    .segments(TrackServiceImpl.toSegments(route.segments()))
                    .bbox(route.bbox())
                    .build());
        }
        result.sort(Comparator.comparing(TrackResponses.MyTripRoute::startDate,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result.size() > MY_ROUTES_TRIP_LIMIT ? List.copyOf(result.subList(0, MY_ROUTES_TRIP_LIMIT)) : result;
    }

    @Override
    @Transactional(readOnly = true)
    public TrackResponses.ShareRoute shareRoute(UUID userId, UUID tripId, LocalDate date) {
        Trip trip = tripAccessGuard.requireAccess(tripId, userId);
        requireEnabled(userId);
        int padDays = businessConfig.getInt(BusinessConfigKey.TRACKER_WINDOW_PAD_DAYS);
        List<TrackDay> days = trackRepository.findDays(tripId, List.of(userId), null, DAY_LIMIT).stream()
                .filter(day -> date == null
                        ? TrackServiceImpl.inWindow(trip, day.getLocalDate(), padDays)
                        : day.getLocalDate().equals(date))
                .toList();
        if (days.isEmpty()) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Nothing recorded to share");
        }
        // No settings row means the defaults, which trim (TripTrackSettings.defaults).
        boolean trim = trackRepository.findSettings(tripId, userId)
                .map(settings -> Boolean.TRUE.equals(settings.getTrimDayEnds()))
                .orElse(true);
        List<PrivacyZoneClipper.Zone> zones = zonesByUser(List.of(userId)).getOrDefault(userId, List.of());

        List<TrackSegmentLine> segments = new ArrayList<>();
        Set<String> provinceCodes = new TreeSet<>();
        double[] bbox = null;
        long distance = 0;
        for (TrackDay day : days) {
            List<TrackSegmentLine> lines = TrackServiceImpl.readSegments(objectMapper, day.getLineSegments());
            if (trim) {
                lines = TrackDayTrimmer.trim(lines, PUBLIC_TRIM_M);
            }
            PrivacyZoneClipper.Clipped clipped = PrivacyZoneClipper.clip(lines, zones);
            segments.addAll(clipped.segments());
            bbox = union(bbox, clipped.bbox());
            distance += nullToZero(day.getDistanceM());
            provinceCodes.addAll(TrackServiceImpl.readProvinceCodes(objectMapper, day.getProvinceCodes()));
        }
        return new TrackResponses.ShareRoute(
                TrackServiceImpl.toSegments(segments),
                bbox,
                distance,
                days.size(),
                toProvinces(trackRepository.findProvinces(List.copyOf(provinceCodes))));
    }

    // --- read by other services ------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<TrackResponses.BookRoute> bookRoute(UUID tripId, UUID ownerId) {
        if (!TrackServiceImpl.isEnabled(businessConfig, betaAccess, ownerId)) {
            return Optional.empty();
        }
        // The book is read by every member: a route its owner keeps private stays out of it.
        if (!TrackServiceImpl.isVisibleToMembers(trackRepository.findSettings(tripId, ownerId).orElse(null))) {
            return Optional.empty();
        }
        List<TrackDay> days = trackRepository.findDays(tripId, List.of(ownerId), null, DAY_LIMIT);
        if (days.isEmpty()) {
            return Optional.empty();
        }
        List<PrivacyZoneClipper.Zone> zones = zonesByUser(List.of(ownerId)).getOrDefault(ownerId, List.of());
        List<TrackSegmentLine> segments = new ArrayList<>();
        double[] bbox = null;
        long distance = 0;
        for (TrackDay day : days) {
            PrivacyZoneClipper.Clipped clipped =
                    PrivacyZoneClipper.clip(TrackServiceImpl.readSegments(objectMapper, day.getLineSegments()), zones);
            segments.addAll(clipped.segments());
            bbox = union(bbox, clipped.bbox());
            distance += nullToZero(day.getDistanceM());
        }
        if (segments.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TrackResponses.BookRoute(TrackServiceImpl.toSegments(segments), bbox, distance));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> distanceByUser(UUID tripId) {
        Map<UUID, Long> result = new LinkedHashMap<>();
        for (TrackUserAggregate row : trackRepository.findDistanceByUser(tripId, MEMBER_LIMIT)) {
            result.put(row.getUserId(), row.getDistanceM() == null ? 0 : row.getDistanceM());
        }
        return result;
    }

    // --- helpers --------------------------------------------------------------------------

    private void requireEnabled(UUID userId) {
        if (!TrackServiceImpl.isEnabled(businessConfig, betaAccess, userId)) {
            throw new BusinessException(ErrorConstant.TRACK_DISABLED, "The travel tracker is not available");
        }
    }

    private Map<UUID, List<PrivacyZoneClipper.Zone>> zonesByUser(List<UUID> userIds) {
        Map<UUID, List<PrivacyZoneClipper.Zone>> zones = new HashMap<>();
        for (PrivacyZone zone : trackRepository.findZonesForUsers(userIds, ZONE_LIMIT)) {
            zones.computeIfAbsent(zone.getUserId(), ignored -> new ArrayList<>())
                    .add(TrackServiceImpl.toClipZone(zone));
        }
        return zones;
    }

    private static List<TrackResponses.Province> toProvinces(List<TrackProvince> provinces) {
        return provinces.stream().map(province -> new TrackResponses.Province(province.getCode(), province.getName()))
                .toList();
    }

    private static double[] union(double[] a, double[] b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return new double[] {Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[2], b[2]), Math.max(a[3], b[3])};
    }

    private static long nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
