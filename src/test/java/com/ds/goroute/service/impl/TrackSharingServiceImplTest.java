package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.TrackPointsPayload;
import com.ds.goroute.dto.request.UploadTrackChunkRequest;
import com.ds.goroute.dto.response.TrackResponses;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.PrivacyZone;
import com.ds.goroute.entity.TrackProvince;
import com.ds.goroute.entity.TrackUserAggregate;
import com.ds.goroute.entity.TrackVisit;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.entity.TripTrackSettings;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.ActivityService;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.TrackSharingService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.TripRealtimePublisher;
import com.ds.goroute.service.track.PolylineCodec;
import com.ds.goroute.service.track.TrackPoint;
import com.ds.goroute.service.track.TrackPointsCodec;
import com.ds.goroute.service.track.TrackStopNamer;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.MemberStatus;
import com.ds.goroute.type.TrackAccuracyMode;
import com.ds.goroute.type.TrackSource;
import com.ds.goroute.type.TrackVisibility;
import com.ds.goroute.type.TrackVisitKind;
import com.ds.goroute.type.TrackVisitStatus;
import com.ds.goroute.type.TripVisibility;
import com.ds.goroute.utils.GeoDistance;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrackSharingServiceImplTest {

    private static final UUID TRIP_ID = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID MEMBER = UUID.randomUUID();
    private static final LocalDate DAY2 = LocalDate.of(2026, 10, 2);
    private static final LocalDate DAY3 = LocalDate.of(2026, 10, 3);
    /** 09:00 in Da Lat. */
    private static final long DAY2_0900 = Instant.parse("2026-10-02T02:00:00Z").toEpochMilli();
    private static final long DAY3_0900 = Instant.parse("2026-10-03T02:00:00Z").toEpochMilli();

    private final InMemoryTrackRepository repository = new InMemoryTrackRepository();
    private final TripAccessGuard guard = mock(TripAccessGuard.class);
    private final TripMemberRepository members = mock(TripMemberRepository.class);
    private final TripRepository trips = mock(TripRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ActivityRepository activities = mock(ActivityRepository.class);
    private final BusinessConfigService config = mock(BusinessConfigService.class);
    private final BetaAccessService beta = mock(BetaAccessService.class);

    private TrackServiceImpl recorder;
    private TrackSharingServiceImpl service;
    private Trip trip;

    @BeforeEach
    void setUp() {
        trip = Trip.builder()
                .id(TRIP_ID)
                .ownerId(OWNER)
                .name("Đà Lạt")
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 10, 4))
                .timezone("Asia/Ho_Chi_Minh")
                .visibility(TripVisibility.PUBLIC)
                .build();
        lenient().when(guard.requireAccess(eq(TRIP_ID), any())).thenReturn(trip);
        lenient().when(guard.requirePublicView(eq(TRIP_ID), any())).thenReturn(trip);
        lenient().when(members.findByTripId(TRIP_ID)).thenReturn(List.of(TripMember.builder()
                .tripId(TRIP_ID).userId(MEMBER).status(MemberStatus.ACCEPTED).build()));
        lenient().when(users.findByIds(any())).thenReturn(List.of(
                User.builder().id(OWNER).fullName("Lan").build()));
        lenient().when(config.getInt(any())).thenAnswer(invocation ->
                ((BusinessConfigKey) invocation.getArgument(0)).defaultInt());
        lenient().when(config.getBoolean(BusinessConfigKey.TRACKER_ENABLED)).thenReturn(true);
        lenient().when(activities.findByTripIdAndDayNumber(eq(TRIP_ID), anyInt())).thenReturn(List.of());

        ObjectMapper objectMapper = new ObjectMapper();
        recorder = new TrackServiceImpl(repository, guard, members, users, activities, mock(ActivityService.class),
                mock(TrackStopNamer.class), config, beta, mock(TripRealtimePublisher.class), objectMapper);
        service = new TrackSharingServiceImpl(repository, trips, guard, members, users, activities, config, beta, objectMapper);
        // 10:00 in Da Lat on day 3.
        service.clock = Clock.fixed(Instant.parse("2026-10-03T03:00:00Z"), ZoneOffset.UTC);
    }

    // --- public page ----------------------------------------------------------------------

    @Test
    void publicPageShowsOnlyMembersWhoChosePublicViewersAndHidesTodayUntilACheckIn() {
        share(OWNER, TrackVisibility.PUBLIC_VIEWERS, false);
        share(MEMBER, TrackVisibility.MEMBERS, false);
        record(OWNER, walk(DAY2_0900, 30));
        record(OWNER, walk(DAY3_0900, 30));
        record(MEMBER, walk(DAY2_0900, 30));

        List<TrackResponses.PublicDay> days = service.publicTrack(null, TRIP_ID);

        assertThat(days).singleElement().satisfies(day -> {
            assertThat(day.userId()).isEqualTo(OWNER);
            assertThat(day.date()).isEqualTo(DAY2);
            assertThat(day.displayName()).isEqualTo("Lan");
            assertThat(day.segments()).isNotEmpty();
        });
    }

    @Test
    void todayIsDrawnOnlyUpToTheLastCheckIn() {
        share(OWNER, TrackVisibility.PUBLIC_VIEWERS, false);
        record(OWNER, walk(DAY3_0900, 30));
        // Checked in 100 seconds into the walk: 10 points, about 100 m.
        repository.latestCheckins.add(new TrackUserAggregate(OWNER, null,
                LocalDateTime.ofInstant(Instant.ofEpochMilli(DAY3_0900 + 100_000), ZoneOffset.UTC)));

        List<TrackResponses.PublicDay> days = service.publicTrack(null, TRIP_ID);

        assertThat(days).singleElement().satisfies(day -> {
            assertThat(day.date()).isEqualTo(DAY3);
            assertThat(day.distanceM()).isBetween(100, 125);
            List<double[]> line = PolylineCodec.decode(day.segments().get(0).polyline());
            assertThat(line.get(line.size() - 1)[0]).isLessThanOrEqualTo(11.9411);
        });
    }

    @Test
    void publicDaysLoseTheirEndsAndThePrivacyZones() {
        share(OWNER, TrackVisibility.PUBLIC_VIEWERS, true);
        // 200 points, about 2.2 km north from 11.94.
        record(OWNER, walk(DAY2_0900, 200));
        repository.zones.add(PrivacyZone.builder().id(UUID.randomUUID()).userId(OWNER).name("Nhà")
                .latitude(new BigDecimal("11.95")).longitude(new BigDecimal("108.44")).radiusM(200)
                .dataVersion(1L).build());

        TrackResponses.PublicDay day = service.publicTrack(null, TRIP_ID).get(0);

        List<double[]> visible = new ArrayList<>();
        day.segments().forEach(segment -> visible.addAll(PolylineCodec.decode(segment.polyline())));
        assertThat(day.segments()).hasSize(2);
        for (double[] point : visible) {
            assertThat(GeoDistance.between(point[0], point[1], 11.94, 108.44)).isGreaterThan(295);
            assertThat(GeoDistance.between(point[0], point[1], 11.95, 108.44)).isGreaterThan(195);
        }
    }

    @Test
    void publicPageIsEmptyWhenTheOwnerIsNotInTheBetaAndTheFeatureIsOff() {
        share(OWNER, TrackVisibility.PUBLIC_VIEWERS, false);
        record(OWNER, walk(DAY2_0900, 30));
        when(config.getBoolean(BusinessConfigKey.TRACKER_ENABLED)).thenReturn(false);

        assertThat(service.publicTrack(null, TRIP_ID)).isEmpty();
    }

    // --- GPX -----------------------------------------------------------------------------

    @Test
    void gpxHasTheRawPointsAndFallsBackToTheLineOnceTheyExpire() {
        record(OWNER, walk(DAY2_0900, 30));

        TrackSharingService.GpxExport raw = service.exportGpx(OWNER, TRIP_ID, DAY2);
        String rawXml = new String(raw.content(), StandardCharsets.UTF_8);
        assertThat(raw.fileName()).isEqualTo("vietdetour-" + TRIP_ID.toString().substring(0, 8) + "-2026-10-02.gpx");
        assertThat(rawXml.split("<trkpt ", -1)).hasSize(31);
        assertThat(rawXml).contains("<time>2026-10-02T02:00:00Z</time>").contains("<ele>1500</ele>");

        repository.chunks.clear();
        String simplified = new String(service.exportGpx(OWNER, TRIP_ID, null).content(), StandardCharsets.UTF_8);
        assertThat(simplified).contains("<trkpt ").doesNotContain("<time>");
        assertThat(service.exportGpx(OWNER, TRIP_ID, null).fileName()).endsWith("-all.gpx");

        assertThatThrownBy(() -> service.exportGpx(OWNER, TRIP_ID, DAY3))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.NOT_FOUND));
    }

    // --- recap and totals ------------------------------------------------------------------

    @Test
    void recapAddsUpTheDaysAndCountsPlannedPlacesReachedOrCheckedIn() {
        repository.provinceCodesForRoute = List.of("68");
        repository.provinces.add(new TrackProvince("68", "Lâm Đồng"));
        record(OWNER, walk(DAY2_0900, 30));
        record(OWNER, walk(DAY3_0900, 30));
        UUID market = UUID.randomUUID();
        UUID lake = UUID.randomUUID();
        UUID museum = UUID.randomUUID();
        when(activities.findByTripId(TRIP_ID)).thenReturn(List.of(
                place(market), place(lake), place(museum),
                Activity.builder().id(UUID.randomUUID()).lat(BigDecimal.ONE).lng(BigDecimal.ONE)
                        .endLat(BigDecimal.TEN).endLng(BigDecimal.TEN).build()));
        repository.visits.add(TrackVisit.builder().id(UUID.randomUUID()).tripId(TRIP_ID).userId(OWNER)
                .localDate(DAY2).kind(TrackVisitKind.ACTIVITY).activityId(market)
                .status(TrackVisitStatus.VISITED).build());
        repository.checkedInActivityIds.addAll(List.of(market, lake));
        repository.checkinCount = 4;

        TrackResponses.Recap recap = service.recap(MEMBER, TRIP_ID, OWNER);

        assertThat(recap.userId()).isEqualTo(OWNER);
        assertThat(recap.dayCount()).isEqualTo(2);
        assertThat(recap.distanceM()).isBetween(600L, 680L);
        assertThat(recap.provincesPassed()).containsExactly(new TrackResponses.Province("68", "Lâm Đồng"));
        assertThat(recap.plannedActivityCount()).isEqualTo(3);
        assertThat(recap.visitedActivityCount()).isEqualTo(2);
        assertThat(recap.checkinCount()).isEqualTo(4);
    }

    @Test
    void recapOfAPrivateRouteIsRefusedToOthers() {
        share(OWNER, TrackVisibility.PRIVATE, true);

        assertThatThrownBy(() -> service.recap(MEMBER, TRIP_ID, OWNER))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.TRACK_NOT_VISIBLE));
    }

    @Test
    void lifetimeStatsAddEveryTripAndListTheProvincesPassed() {
        repository.provinceCodesForRoute = List.of("68");
        repository.provinces.add(new TrackProvince("68", "Lâm Đồng"));
        record(OWNER, walk(DAY2_0900, 30));

        TrackResponses.MyStats stats = service.myStats(OWNER);

        assertThat(stats.tripCount()).isEqualTo(1);
        assertThat(stats.totalDistanceM()).isBetween(300L, 340L);
        assertThat(stats.provincesPassed()).extracting(TrackResponses.Province::name).containsExactly("Lâm Đồng");
    }

    // --- borrowed by other features ---------------------------------------------------------

    @Test
    void bookRouteIsTheOwnersWholeRouteUnlessPrivate() {
        record(OWNER, walk(DAY2_0900, 30));
        record(OWNER, walk(DAY3_0900, 30));

        assertThat(service.bookRoute(TRIP_ID, OWNER)).hasValueSatisfying(route -> {
            assertThat(route.segments()).hasSize(2);
            assertThat(route.distanceM()).isBetween(600L, 680L);
            assertThat(route.bbox()).hasSize(4);
        });
        assertThat(service.distanceByUser(TRIP_ID)).containsOnlyKeys(OWNER);

        share(OWNER, TrackVisibility.PRIVATE, true);
        assertThat(service.bookRoute(TRIP_ID, OWNER)).isEmpty();
    }

    // --- overview map and share picture -------------------------------------------------------

    @Test
    void myRoutesListsEachTripOnceWithItsWholeRouteAndSkipsTripsTheCallerLeft() {
        repository.provinceCodesForRoute = List.of("68");
        record(OWNER, walk(DAY2_0900, 30));
        record(OWNER, walk(DAY3_0900, 30));
        when(trips.findByUserId(OWNER)).thenReturn(List.of(trip));

        assertThat(service.myRoutes(OWNER)).singleElement().satisfies(route -> {
            assertThat(route.tripId()).isEqualTo(TRIP_ID);
            assertThat(route.tripName()).isEqualTo("Đà Lạt");
            assertThat(route.dayCount()).isEqualTo(2);
            assertThat(route.provinceCount()).isEqualTo(1);
            assertThat(route.distanceM()).isBetween(600L, 680L);
            assertThat(route.segments()).hasSize(2);
            assertThat(route.bbox()).hasSize(4);
        });

        when(trips.findByUserId(OWNER)).thenReturn(List.of());
        assertThat(service.myRoutes(OWNER)).isEmpty();
    }

    @Test
    void shareRouteIsTheCallersOwnRouteWithoutPrivacyZonesOrDayEnds() {
        // 200 points, about 2.2 km north from 11.94; no settings row trims by default.
        record(OWNER, walk(DAY2_0900, 200));
        repository.zones.add(PrivacyZone.builder().id(UUID.randomUUID()).userId(OWNER).name("Nhà")
                .latitude(new BigDecimal("11.95")).longitude(new BigDecimal("108.44")).radiusM(200)
                .dataVersion(1L).build());

        TrackResponses.ShareRoute route = service.shareRoute(OWNER, TRIP_ID, null);

        assertThat(route.dayCount()).isEqualTo(1);
        assertThat(route.segments()).hasSize(2);
        for (TrackResponses.Segment segment : route.segments()) {
            for (double[] point : PolylineCodec.decode(segment.polyline())) {
                assertThat(GeoDistance.between(point[0], point[1], 11.94, 108.44)).isGreaterThan(295);
                assertThat(GeoDistance.between(point[0], point[1], 11.95, 108.44)).isGreaterThan(195);
            }
        }

        assertThatThrownBy(() -> service.shareRoute(OWNER, TRIP_ID, DAY3))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.NOT_FOUND));
    }

    @Test
    void shareRouteKeepsTheDayEndsWhenTheCallerTurnedTrimmingOff() {
        share(OWNER, TrackVisibility.MEMBERS, false);
        record(OWNER, walk(DAY2_0900, 30));

        TrackResponses.ShareRoute route = service.shareRoute(OWNER, TRIP_ID, DAY2);

        List<double[]> line = PolylineCodec.decode(route.segments().get(0).polyline());
        assertThat(line.get(0)[0]).isEqualTo(11.94);
    }

    // --- helpers ------------------------------------------------------------------------------

    private void share(UUID userId, TrackVisibility visibility, boolean trimDayEnds) {
        repository.settings.removeIf(row -> row.getUserId().equals(userId));
        repository.settings.add(TripTrackSettings.builder().id(UUID.randomUUID()).tripId(TRIP_ID).userId(userId)
                .visibility(visibility).trimDayEnds(trimDayEnds).defaultAccuracy(TrackAccuracyMode.BALANCED)
                .autoEnabled(false).dataVersion(1L).build());
    }

    private void record(UUID userId, List<TrackPoint> points) {
        TrackPointsPayload payload = TrackPointsCodec.encode(points);
        UploadTrackChunkRequest request = new UploadTrackChunkRequest();
        request.setClientChunkId(UUID.randomUUID());
        request.setClientSessionId(UUID.randomUUID());
        request.setAccuracyMode(TrackAccuracyMode.BALANCED);
        request.setSource(TrackSource.RECORD);
        request.setPoints(payload);
        recorder.uploadChunk(userId, TRIP_ID, request);
    }

    /** 11.1 m north every 10 seconds from Da Lat centre. */
    private static List<TrackPoint> walk(long start, int count) {
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            points.add(new TrackPoint(start + i * 10_000L, 11.94 + i * 0.0001, 108.44, 8, 1500, 12));
        }
        return points;
    }

    private static Activity place(UUID id) {
        return Activity.builder().id(id).lat(new BigDecimal("11.94")).lng(new BigDecimal("108.44")).build();
    }
}
