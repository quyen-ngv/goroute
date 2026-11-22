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
import com.ds.goroute.entity.TrackVisit;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.entity.TripTrackSettings;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.ActivityService;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.TrackService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.TripRealtimePublisher;
import com.ds.goroute.service.track.PolylineCodec;
import com.ds.goroute.service.track.TrackPoint;
import com.ds.goroute.service.track.TrackPointsCodec;
import com.ds.goroute.service.track.TrackStopNamer;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.MemberStatus;
import com.ds.goroute.type.TrackAccuracyMode;
import com.ds.goroute.type.TrackDeletionScope;
import com.ds.goroute.type.TrackSource;
import com.ds.goroute.type.TrackVisibility;
import com.ds.goroute.type.TrackVisitKind;
import com.ds.goroute.type.TrackVisitStatus;
import com.ds.goroute.type.TripRealtimeEventType;
import com.ds.goroute.type.TripVisibility;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrackServiceImplTest {

    private static final UUID TRIP_ID = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID MEMBER = UUID.randomUUID();
    private static final UUID MARKET = UUID.randomUUID();
    /** 09:00 in Da Lat on day 2 of the trip. */
    private static final long DAY2_0900 = Instant.parse("2026-10-02T02:00:00Z").toEpochMilli();
    private static final long MINUTE = 60_000L;
    /** A cafe about 2 km from the walk most tests use; nothing is planned there. */
    private static final double CAFE_LAT = 11.9600;
    private static final double CAFE_LNG = 108.4500;

    private final InMemoryTrackRepository repository = new InMemoryTrackRepository();
    private final TripAccessGuard guard = mock(TripAccessGuard.class);
    private final TripMemberRepository members = mock(TripMemberRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ActivityRepository activities = mock(ActivityRepository.class);
    private final ActivityService activityService = mock(ActivityService.class);
    private final TrackStopNamer stopNamer = mock(TrackStopNamer.class);
    private final BusinessConfigService config = mock(BusinessConfigService.class);
    private final BetaAccessService beta = mock(BetaAccessService.class);
    private final TripRealtimePublisher realtime = mock(TripRealtimePublisher.class);

    private TrackServiceImpl service;
    private Trip trip;

    @BeforeEach
    void setUp() {
        trip = Trip.builder()
                .id(TRIP_ID)
                .ownerId(OWNER)
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 10, 3))
                .timezone("Asia/Ho_Chi_Minh")
                .visibility(TripVisibility.PRIVATE)
                .build();
        lenient().when(guard.requireAccess(eq(TRIP_ID), any())).thenReturn(trip);
        lenient().when(members.findByTripId(TRIP_ID)).thenReturn(List.of(TripMember.builder()
                .tripId(TRIP_ID).userId(MEMBER).status(MemberStatus.ACCEPTED).build()));
        lenient().when(users.findByIds(any())).thenReturn(List.of());
        lenient().when(config.getInt(any())).thenAnswer(invocation ->
                ((BusinessConfigKey) invocation.getArgument(0)).defaultInt());
        lenient().when(config.getBoolean(BusinessConfigKey.TRACKER_ENABLED)).thenReturn(true);
        lenient().when(activities.findByTripIdAndDayNumber(eq(TRIP_ID), anyInt())).thenReturn(List.of());

        // Goong naming runs on a worker in production; here it runs inline.
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(stopNamer).submit(any());

        service = new TrackServiceImpl(repository, guard, members, users, activities, activityService, stopNamer,
                config, beta, realtime, new ObjectMapper());
        service.clock = Clock.fixed(Instant.parse("2026-10-02T03:00:00Z"), ZoneOffset.UTC);
    }

    @Test
    void uploadStoresTheChunkAndBuildsTheDay() {
        TrackService.UploadOutcome outcome = service.uploadChunk(OWNER, TRIP_ID, upload(walk(DAY2_0900, 30)));

        assertThat(outcome.replayed()).isFalse();
        assertThat(outcome.result().acceptedPoints()).isEqualTo(30);
        assertThat(outcome.result().days()).singleElement().satisfies(day -> {
            assertThat(day.date()).isEqualTo(LocalDate.of(2026, 10, 2));
            assertThat(day.dayNumber()).isEqualTo(2);
            assertThat(day.distanceM()).isBetween(300, 340);
        });
        assertThat(repository.chunks).hasSize(1);
        verify(realtime).publishAfterCommit(eq(TripRealtimeEventType.TRACK_UPDATED), eq(TRIP_ID), eq(OWNER), eq(OWNER),
                eq(Map.of("dates", List.of("2026-10-02"))));
    }

    @Test
    void resendingTheSameChunkDoesNotStoreItTwice() {
        UploadTrackChunkRequest request = upload(walk(DAY2_0900, 30));
        service.uploadChunk(OWNER, TRIP_ID, request);

        TrackService.UploadOutcome again = service.uploadChunk(OWNER, TRIP_ID, request);

        assertThat(again.replayed()).isTrue();
        assertThat(again.result().acceptedPoints()).isEqualTo(30);
        assertThat(again.result().days()).hasSize(1);
        assertThat(repository.chunks).hasSize(1);
    }

    @Test
    void pointsOutsideTheTripDaysAreRefused() {
        long afterTrip = Instant.parse("2026-10-10T02:00:00Z").toEpochMilli();

        assertBusinessError(ErrorConstant.TRACK_OUTSIDE_TRIP_WINDOW,
                () -> service.uploadChunk(OWNER, TRIP_ID, upload(walk(afterTrip, 5))));
        assertThat(repository.chunks).isEmpty();
    }

    @Test
    void padDayBeforeTheTripIsAccepted() {
        long dayBefore = Instant.parse("2026-09-30T02:00:00Z").toEpochMilli();

        TrackService.UploadOutcome outcome = service.uploadChunk(OWNER, TRIP_ID, upload(walk(dayBefore, 5)));

        assertThat(outcome.result().days()).singleElement()
                .satisfies(day -> assertThat(day.dayNumber()).isZero());
    }

    @Test
    void inaccurateFixesAreCountedAsRejected() {
        List<TrackPoint> points = new ArrayList<>(walk(DAY2_0900, 5));
        points.add(new TrackPoint(DAY2_0900 + 10 * MINUTE, 11.95, 108.44, 400, null, null));

        TrackService.UploadOutcome outcome = service.uploadChunk(OWNER, TRIP_ID, upload(points));

        assertThat(outcome.result().acceptedPoints()).isEqualTo(5);
        assertThat(outcome.result().rejectedPoints()).isEqualTo(1);
    }

    @Test
    void disabledFeatureRefusesUploadsButNotForBetaUsers() {
        when(config.getBoolean(BusinessConfigKey.TRACKER_ENABLED)).thenReturn(false);

        assertBusinessError(ErrorConstant.TRACK_DISABLED,
                () -> service.uploadChunk(OWNER, TRIP_ID, upload(walk(DAY2_0900, 5))));

        when(beta.isBetaUser(OWNER)).thenReturn(true);
        assertThat(service.uploadChunk(OWNER, TRIP_ID, upload(walk(DAY2_0900, 5))).replayed()).isFalse();
    }

    @Test
    void malformedPointsAreRefusedWithTheReason() {
        UploadTrackChunkRequest request = upload(walk(DAY2_0900, 3));
        request.getPoints().setDt(List.of(0L, 0L, 1000L));

        assertBusinessError(ErrorConstant.TRACK_CHUNK_INVALID, () -> service.uploadChunk(OWNER, TRIP_ID, request));
    }

    @Test
    void stayingAtAPlannedActivityIsRecordedAsVisited() {
        when(activities.findByTripIdAndDayNumber(TRIP_ID, 2)).thenReturn(List.of(Activity.builder()
                .id(MARKET).tripId(TRIP_ID).dayNumber(2).name("Chợ Đà Lạt")
                .lat(new BigDecimal("11.9429")).lng(new BigDecimal("108.4372")).build()));
        List<TrackPoint> points = List.of(
                new TrackPoint(DAY2_0900, 11.9500, 108.4372, 8, null, null),
                new TrackPoint(DAY2_0900 + 5 * MINUTE, 11.9430, 108.4372, 8, null, null),
                new TrackPoint(DAY2_0900 + 40 * MINUTE, 11.9500, 108.4372, 8, null, null));

        service.uploadChunk(OWNER, TRIP_ID, upload(points));
        List<TrackResponses.Day> days = service.days(OWNER, TRIP_ID, null, null);

        assertThat(days).singleElement().satisfies(day -> assertThat(day.visits()).singleElement().satisfies(visit -> {
            assertThat(visit.activityId()).isEqualTo(MARKET);
            assertThat(visit.name()).isEqualTo("Chợ Đà Lạt");
            assertThat(visit.departedAt()).isNotNull();
        }));
    }

    @Test
    void membersSeeEachOtherUnlessTheRouteIsPrivate() {
        service.uploadChunk(OWNER, TRIP_ID, upload(walk(DAY2_0900, 10)));
        service.uploadChunk(MEMBER, TRIP_ID, upload(walk(DAY2_0900, 10)));

        assertThat(service.overview(MEMBER, TRIP_ID, null)).extracting(TrackResponses.Day::userId)
                .containsExactlyInAnyOrder(OWNER, MEMBER);

        repository.settings.add(TripTrackSettings.builder().id(UUID.randomUUID()).tripId(TRIP_ID).userId(OWNER)
                .visibility(TrackVisibility.PRIVATE).trimDayEnds(true)
                .defaultAccuracy(TrackAccuracyMode.BALANCED).autoEnabled(false).dataVersion(1L).build());

        assertThat(service.overview(MEMBER, TRIP_ID, null)).extracting(TrackResponses.Day::userId)
                .containsExactly(MEMBER);
        assertBusinessError(ErrorConstant.TRACK_NOT_VISIBLE, () -> service.days(MEMBER, TRIP_ID, OWNER, null));
        assertThat(service.days(OWNER, TRIP_ID, null, null)).hasSize(1);
    }

    @Test
    void privacyZonesAreCutForOthersButNotForTheOwner() {
        service.uploadChunk(OWNER, TRIP_ID, upload(walk(DAY2_0900, 60)));
        // Home sits on the middle of the walk.
        repository.zones.add(PrivacyZone.builder().id(UUID.randomUUID()).userId(OWNER).name("Nhà")
                .latitude(new BigDecimal("11.943")).longitude(new BigDecimal("108.44")).radiusM(200)
                .dataVersion(1L).build());

        TrackResponses.Day own = service.days(OWNER, TRIP_ID, null, null).get(0);
        TrackResponses.Day seenByMember = service.days(MEMBER, TRIP_ID, OWNER, null).get(0);

        assertThat(own.segments()).hasSize(1);
        assertThat(seenByMember.segments()).hasSize(2);
        for (TrackResponses.Segment segment : seenByMember.segments()) {
            for (double[] point : PolylineCodec.decode(segment.polyline())) {
                assertThat(Math.abs(point[0] - 11.943)).isGreaterThan(0.0015);
            }
        }
    }

    @Test
    void deletingATimeRangeRewritesTheDayAndDeletingEverythingRemovesIt() {
        service.uploadChunk(OWNER, TRIP_ID, upload(walk(DAY2_0900, 30)));
        TrackDeletionRequest firstHalf = deletion(TrackDeletionScope.RANGES, DAY2_0900, DAY2_0900 + 14 * 10_000L);

        TrackResponses.Deletion partial = service.delete(OWNER, TRIP_ID, firstHalf);

        assertThat(partial.deletedDates()).isEmpty();
        assertThat(partial.days()).singleElement().satisfies(day -> assertThat(day.pointCount()).isEqualTo(15));

        TrackResponses.Deletion all = service.delete(OWNER, TRIP_ID,
                deletion(TrackDeletionScope.RANGES, DAY2_0900, DAY2_0900 + 3_600_000));

        assertThat(all.deletedDates()).containsExactly(LocalDate.of(2026, 10, 2));
        assertThat(repository.chunks).isEmpty();
        assertThat(repository.days).isEmpty();
    }

    @Test
    void deletingIsAllowedEvenWhenTheFeatureIsOff() {
        service.uploadChunk(OWNER, TRIP_ID, upload(walk(DAY2_0900, 10)));
        when(config.getBoolean(BusinessConfigKey.TRACKER_ENABLED)).thenReturn(false);

        TrackDeletionRequest request = new TrackDeletionRequest();
        request.setScope(TrackDeletionScope.TRIP);
        service.delete(OWNER, TRIP_ID, request);

        assertThat(repository.days).isEmpty();
    }

    @Test
    void settingsUseOptimisticVersions() {
        TrackResponses.Settings first = service.updateSettings(OWNER, TRIP_ID,
                settingsRequest(TrackVisibility.PRIVATE, 0));
        assertThat(first.dataVersion()).isEqualTo(1);
        assertThat(first.visibility()).isEqualTo(TrackVisibility.PRIVATE);

        assertBusinessError(ErrorConstant.ALREADY_PROCESSED,
                () -> service.updateSettings(OWNER, TRIP_ID, settingsRequest(TrackVisibility.MEMBERS, 0)));
        assertThat(service.updateSettings(OWNER, TRIP_ID, settingsRequest(TrackVisibility.MEMBERS, 1)).dataVersion())
                .isEqualTo(2);
    }

    @Test
    void publicViewersNeedASharedOrPublicTrip() {
        assertBusinessError(ErrorConstant.INVALID_PARAMETERS,
                () -> service.updateSettings(OWNER, TRIP_ID, settingsRequest(TrackVisibility.PUBLIC_VIEWERS, 0)));

        trip.setVisibility(TripVisibility.SHARED);
        assertThat(service.updateSettings(OWNER, TRIP_ID, settingsRequest(TrackVisibility.PUBLIC_VIEWERS, 0))
                .visibility()).isEqualTo(TrackVisibility.PUBLIC_VIEWERS);
    }

    @Test
    void atMostFivePrivacyZones() {
        for (int i = 0; i < 5; i++) {
            service.createPrivacyZone(OWNER, zoneRequest("Zone " + i));
        }

        assertBusinessError(ErrorConstant.TRACK_PRIVACY_ZONE_LIMIT,
                () -> service.createPrivacyZone(OWNER, zoneRequest("Sixth")));
        assertBusinessError(ErrorConstant.NOT_FOUND, () -> service.deletePrivacyZone(MEMBER,
                repository.zones.get(0).getId()));
    }

    @Test
    void leavingATripTakesTheMembersRouteWithIt() {
        service.uploadChunk(MEMBER, TRIP_ID, upload(walk(DAY2_0900, 10)));
        service.uploadChunk(OWNER, TRIP_ID, upload(walk(DAY2_0900, 10)));

        service.onMemberLeft(TRIP_ID, MEMBER);

        assertThat(repository.days).extracting(day -> day.getUserId()).containsExactly(OWNER);
        assertThat(repository.chunks).extracting(chunk -> chunk.getUserId()).containsExactly(OWNER);
        verify(realtime, atLeastOnce()).publishAfterCommit(eq(TripRealtimeEventType.TRACK_UPDATED), eq(TRIP_ID),
                eq(MEMBER), eq(MEMBER), anyMap());
    }

    @Test
    void contextReportsWindowMembersAndWhetherRecordingIsPossible() {
        service.uploadChunk(OWNER, TRIP_ID, upload(walk(DAY2_0900, 10)));

        TrackResponses.Context context = service.context(MEMBER, TRIP_ID);

        assertThat(context.enabled()).isTrue();
        assertThat(context.canRecord()).isTrue();
        assertThat(context.window().timezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(context.mySettings().dataVersion()).isZero();
        assertThat(context.members()).extracting(TrackResponses.Member::userId).containsExactly(OWNER, MEMBER);
        assertThat(context.members().get(0).hasTrack()).isTrue();
        assertThat(context.members().get(1).isMe()).isTrue();
        assertThat(context.config().arrivalRadiusM()).isEqualTo(100);

        service.clock = Clock.fixed(Instant.parse("2026-10-20T03:00:00Z"), ZoneOffset.UTC);
        assertThat(service.context(MEMBER, TRIP_ID).canRecord()).isFalse();
    }

    // --- v1.1 ------------------------------------------------------------------------------

    @Test
    void rebuildRecordsTheProvincesAndFlagsDaysOutsideTheCurrentWindow() {
        repository.provinceCodesForRoute = List.of("68");
        service.uploadChunk(OWNER, TRIP_ID, upload(walk(DAY2_0900, 30)));

        assertThat(repository.lastRouteWkt).startsWith("MULTILINESTRING((108.440000 11.940000,");
        TrackResponses.Day day = service.days(OWNER, TRIP_ID, null, null).get(0);
        assertThat(day.provinceCodes()).containsExactly("68");
        assertThat(day.outsideTripWindow()).isFalse();

        // The trip moved a week later (TR-05): the old day is kept but flagged.
        trip.setStartDate(LocalDate.of(2026, 10, 8));
        trip.setEndDate(LocalDate.of(2026, 10, 10));
        assertThat(service.days(OWNER, TRIP_ID, null, null).get(0).outsideTripWindow()).isTrue();
    }

    @Test
    void aLongStopAwayFromThePlanBecomesASuggestionNamedFromTheCatalogue() {
        UUID placeId = UUID.randomUUID();
        when(stopNamer.nearestCatalogPlace(anyDouble(), anyDouble(), eq(60)))
                .thenReturn(Optional.of(new TrackStopNamer.CatalogPlace(placeId, "Cà phê Tùng")));

        service.uploadChunk(OWNER, TRIP_ID, upload(stopAt(CAFE_LAT, CAFE_LNG)));

        TrackResponses.Visit visit = service.days(OWNER, TRIP_ID, null, null).get(0).visits().get(0);
        assertThat(visit.kind()).isEqualTo(TrackVisitKind.OFF_PLAN);
        assertThat(visit.status()).isEqualTo(TrackVisitStatus.SUGGESTED);
        assertThat(visit.name()).isEqualTo("Cà phê Tùng");
        assertThat(visit.placeId()).isEqualTo(placeId);
        verify(stopNamer, never()).reverseGeocode(any(), anyDouble(), anyDouble());
    }

    @Test
    void aStopAtAPlannedActivityIsNotAnOffPlanStop() {
        when(activities.findByTripIdAndDayNumber(TRIP_ID, 2)).thenReturn(List.of(Activity.builder()
                .id(MARKET).tripId(TRIP_ID).dayNumber(2).name("Cafe")
                .lat(BigDecimal.valueOf(CAFE_LAT)).lng(BigDecimal.valueOf(CAFE_LNG)).build()));

        service.uploadChunk(OWNER, TRIP_ID, upload(stopAt(CAFE_LAT, CAFE_LNG)));

        assertThat(repository.visits).extracting(TrackVisit::getKind).containsExactly(TrackVisitKind.ACTIVITY);
    }

    @Test
    void withoutACatalogPlaceGoongNamesTheStopAfterwardsAndAFailureLeavesItUnnamed() {
        when(stopNamer.reverseGeocode(any(), anyDouble(), anyDouble())).thenReturn("12 Nguyễn Chí Thanh");

        service.uploadChunk(OWNER, TRIP_ID, upload(stopAt(CAFE_LAT, CAFE_LNG)));

        assertThat(repository.visits).singleElement()
                .satisfies(visit -> assertThat(visit.getName()).isEqualTo("12 Nguyễn Chí Thanh"));

        repository.visits.clear();
        when(stopNamer.reverseGeocode(any(), anyDouble(), anyDouble())).thenThrow(new IllegalStateException("down"));
        TrackService.UploadOutcome outcome = service.uploadChunk(OWNER, TRIP_ID,
                upload(stopAt(CAFE_LAT + 0.05, CAFE_LNG, DAY2_0900 + 60 * MINUTE)));

        assertThat(outcome.result().acceptedPoints()).isEqualTo(4);
        assertThat(repository.visits).filteredOn(visit -> visit.getKind() == TrackVisitKind.OFF_PLAN)
                .isNotEmpty()
                .allSatisfy(visit -> assertThat(visit.getName()).isNull());
    }

    @Test
    void aDismissedStopStaysDismissedWhenTheDayIsRebuiltAndAStaleSuggestionIsRemoved() {
        service.uploadChunk(OWNER, TRIP_ID, upload(stopAt(CAFE_LAT, CAFE_LNG)));
        UUID visitId = repository.visits.get(0).getId();

        TrackResponses.Visit dismissed = service.dismissVisit(OWNER, TRIP_ID, visitId);
        assertThat(dismissed.status()).isEqualTo(TrackVisitStatus.DISMISSED);

        // A later chunk of the same day rebuilds it: the same stop keeps its row and status.
        service.uploadChunk(OWNER, TRIP_ID, upload(walk(DAY2_0900 + 120 * MINUTE, 5)));
        assertThat(repository.visits).singleElement().satisfies(visit -> {
            assertThat(visit.getId()).isEqualTo(visitId);
            assertThat(visit.getStatus()).isEqualTo(TrackVisitStatus.DISMISSED);
        });

        // A second stop, then its points are deleted: the suggestion goes, the dismissed one stays.
        service.uploadChunk(OWNER, TRIP_ID, upload(stopAt(CAFE_LAT + 0.05, CAFE_LNG, DAY2_0900 + 200 * MINUTE)));
        assertThat(repository.visits).hasSize(2);
        service.delete(OWNER, TRIP_ID, deletion(TrackDeletionScope.RANGES,
                DAY2_0900 + 200 * MINUTE, DAY2_0900 + 300 * MINUTE));
        assertThat(repository.visits).extracting(TrackVisit::getId).containsExactly(visitId);
    }

    @Test
    void onlyOffPlanStopsCanBeDismissedAndOnlyOwnOnes() {
        TrackVisit activityVisit = TrackVisit.builder().id(UUID.randomUUID()).tripId(TRIP_ID).userId(OWNER)
                .localDate(LocalDate.of(2026, 10, 2)).kind(TrackVisitKind.ACTIVITY).status(TrackVisitStatus.VISITED)
                .build();
        repository.visits.add(activityVisit);

        assertBusinessError(ErrorConstant.TRACK_VISIT_NOT_OFF_PLAN,
                () -> service.dismissVisit(OWNER, TRIP_ID, activityVisit.getId()));
        assertBusinessError(ErrorConstant.TRACK_VISIT_NOT_FOUND,
                () -> service.dismissVisit(MEMBER, TRIP_ID, activityVisit.getId()));
    }

    @Test
    void addingAStopCreatesAnActivityAtTheRoundedArrivalTimeOnceOnly() {
        UUID placeId = UUID.randomUUID();
        when(stopNamer.nearestCatalogPlace(anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Optional.of(new TrackStopNamer.CatalogPlace(placeId, "Cà phê Tùng")));
        when(guard.requireEditAccess(TRIP_ID, OWNER)).thenReturn(trip);
        UUID activityId = UUID.randomUUID();
        when(activityService.createActivity(eq(TRIP_ID), any(), eq(OWNER)))
                .thenReturn(ActivityResponse.builder().id(activityId).build());
        service.uploadChunk(OWNER, TRIP_ID, upload(stopAt(CAFE_LAT, CAFE_LNG)));
        UUID visitId = repository.visits.get(0).getId();

        TrackService.AddOutcome first = service.addVisitToItinerary(OWNER, TRIP_ID, visitId, null);
        TrackService.AddOutcome again = service.addVisitToItinerary(OWNER, TRIP_ID, visitId, null);

        ArgumentCaptor<CreateActivityRequest> created = ArgumentCaptor.forClass(CreateActivityRequest.class);
        verify(activityService, times(1)).createActivity(eq(TRIP_ID), created.capture(), eq(OWNER));
        assertThat(created.getValue().getName()).isEqualTo("Cà phê Tùng");
        assertThat(created.getValue().getDayNumber()).isEqualTo(2);
        // Arrived 09:02 local, snapped to 09:00.
        assertThat(created.getValue().getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(created.getValue().getPlaceId()).isEqualTo(placeId.toString());
        assertThat(first.alreadyAdded()).isFalse();
        assertThat(first.result().activityId()).isEqualTo(activityId);
        assertThat(first.result().visit().status()).isEqualTo(TrackVisitStatus.ADDED);
        assertThat(first.result().visit().createdActivityId()).isEqualTo(activityId);
        assertThat(again.alreadyAdded()).isTrue();
        assertThat(again.result().activityId()).isEqualTo(activityId);
    }

    @Test
    void anUnnamedStopNeedsANameToBeAdded() {
        when(guard.requireEditAccess(TRIP_ID, OWNER)).thenReturn(trip);
        service.uploadChunk(OWNER, TRIP_ID, upload(stopAt(CAFE_LAT, CAFE_LNG)));
        UUID visitId = repository.visits.get(0).getId();

        assertBusinessError(ErrorConstant.INVALID_PARAMETERS,
                () -> service.addVisitToItinerary(OWNER, TRIP_ID, visitId, new AddTrackVisitToItineraryRequest()));
    }

    @Test
    void startTimeSnapsToFiveMinutesWithoutLeavingTheDay() {
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        assertThat(TrackServiceImpl.roundedLocalTime(LocalDateTime.of(2026, 10, 2, 2, 3), zone))
                .isEqualTo(LocalTime.of(9, 5));
        assertThat(TrackServiceImpl.roundedLocalTime(LocalDateTime.of(2026, 10, 2, 16, 59), zone))
                .isEqualTo(LocalTime.of(23, 55));
    }

    @Test
    void autoRecordingIsStoredAndKeptWhenNotSent() {
        UpdateTrackSettingsRequest on = settingsRequest(TrackVisibility.MEMBERS, 0);
        on.setAutoEnabled(true);
        assertThat(service.updateSettings(OWNER, TRIP_ID, on).autoEnabled()).isTrue();

        TrackResponses.Settings kept = service.updateSettings(OWNER, TRIP_ID, settingsRequest(TrackVisibility.MEMBERS, 1));
        assertThat(kept.autoEnabled()).isTrue();
        assertThat(service.context(OWNER, TRIP_ID).mySettings().autoEnabled()).isTrue();
    }

    @Test
    void contextCarriesTheOffPlanThresholds() {
        TrackResponses.Config config = service.context(OWNER, TRIP_ID).config();

        assertThat(config.offPlanRadiusM()).isEqualTo(100);
        assertThat(config.offPlanMinMinutes()).isEqualTo(10);
        assertThat(config.offPlanNameRadiusM()).isEqualTo(60);
    }

    // --- helpers ------------------------------------------------------------------------

    /** Arrives at the spot at 09:02, stays until 09:25, leaves at 09:30 (well past ten minutes). */
    private static List<TrackPoint> stopAt(double latitude, double longitude) {
        return stopAt(latitude, longitude, DAY2_0900);
    }

    private static List<TrackPoint> stopAt(double latitude, double longitude, long start) {
        return List.of(
                new TrackPoint(start, latitude - 0.02, longitude, 8, null, null),
                new TrackPoint(start + 2 * MINUTE, latitude, longitude, 8, null, null),
                new TrackPoint(start + 25 * MINUTE, latitude + 0.0001, longitude, 8, null, null),
                new TrackPoint(start + 30 * MINUTE, latitude + 0.02, longitude, 8, null, null));
    }

    /** 11.1 m north every 10 seconds from Da Lat centre. */
    private static List<TrackPoint> walk(long start, int count) {
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            points.add(new TrackPoint(start + i * 10_000L, 11.94 + i * 0.0001, 108.44, 8, 1500, 12));
        }
        return points;
    }

    private static UploadTrackChunkRequest upload(List<TrackPoint> points) {
        TrackPointsPayload payload = TrackPointsCodec.encode(points);
        UploadTrackChunkRequest request = new UploadTrackChunkRequest();
        request.setClientChunkId(UUID.randomUUID());
        request.setClientSessionId(UUID.randomUUID());
        request.setAccuracyMode(TrackAccuracyMode.BALANCED);
        request.setSource(TrackSource.RECORD);
        request.setPoints(payload);
        return request;
    }

    private static TrackDeletionRequest deletion(TrackDeletionScope scope, long fromMs, long toMs) {
        TrackDeletionRequest.Range range = new TrackDeletionRequest.Range();
        range.setFromMs(fromMs);
        range.setToMs(toMs);
        TrackDeletionRequest request = new TrackDeletionRequest();
        request.setScope(scope);
        request.setDate(LocalDate.of(2026, 10, 2));
        request.setRanges(List.of(range));
        return request;
    }

    private static UpdateTrackSettingsRequest settingsRequest(TrackVisibility visibility, long expectedVersion) {
        UpdateTrackSettingsRequest request = new UpdateTrackSettingsRequest();
        request.setVisibility(visibility);
        request.setTrimDayEnds(true);
        request.setDefaultAccuracy(TrackAccuracyMode.BALANCED);
        request.setExpectedVersion(expectedVersion);
        return request;
    }

    private static PrivacyZoneRequest zoneRequest(String name) {
        PrivacyZoneRequest request = new PrivacyZoneRequest();
        request.setName(name);
        request.setLatitude(new BigDecimal("11.94"));
        request.setLongitude(new BigDecimal("108.44"));
        request.setRadiusM(500);
        return request;
    }

    private static void assertBusinessError(int code, Executable call) {
        assertThatThrownBy(call::execute)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode()).isEqualTo(code));
    }
}
