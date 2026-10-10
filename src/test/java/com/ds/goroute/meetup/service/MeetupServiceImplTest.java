package com.ds.goroute.meetup.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.entity.Ward;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.meetup.domain.Meetup;
import com.ds.goroute.meetup.domain.MeetupParticipant;
import com.ds.goroute.meetup.dto.MeetupContextResponse;
import com.ds.goroute.meetup.dto.MeetupDetailResponse;
import com.ds.goroute.meetup.dto.MeetupJoinResponse;
import com.ds.goroute.meetup.dto.MeetupParticipantResponse;
import com.ds.goroute.meetup.dto.MeetupUpsertRequest;
import com.ds.goroute.meetup.persistence.MeetupRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.GeoService;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.UserBlockService;
import com.ds.goroute.service.notification.NotificationMessage;
import com.ds.goroute.service.notification.NotificationTemplateRenderer;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.MeetupCannotHostReason;
import com.ds.goroute.type.MeetupCategory;
import com.ds.goroute.type.MeetupEffectiveStatus;
import com.ds.goroute.type.MeetupJoinPolicy;
import com.ds.goroute.type.MeetupLocationMode;
import com.ds.goroute.type.MeetupParticipantStatus;
import com.ds.goroute.type.MeetupStatus;
import com.ds.goroute.type.MeetupTimeMode;
import com.ds.goroute.type.MeetupViewerRole;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.utils.GeoDistance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MeetupServiceImpl")
class MeetupServiceImplTest {

    /** 10:00 in Vietnam, 4 October 2026. */
    static final Instant NOW = Instant.parse("2026-10-04T03:00:00Z");
    static final LocalDateTime NOW_UTC = LocalDateTime.of(2026, 10, 4, 3, 0);
    static final LocalDate TODAY = LocalDate.of(2026, 10, 4);
    static final BigDecimal LAT = new BigDecimal("21.0500000");
    static final BigDecimal LNG = new BigDecimal("105.8200000");

    @Mock MeetupRepository repository;
    @Mock BusinessConfigService config;
    @Mock BetaAccessService betaAccess;
    @Mock GeoService geoService;
    @Mock UserBlockService userBlocks;
    @Mock ContentModerationService moderation;
    @Mock UserRepository users;
    @Mock NotificationTemplateRenderer renderer;
    @Mock NotificationService notifications;
    @Mock MeetupChatService meetupChat;

    MeetupServiceImpl service;

    final UUID hostId = UUID.randomUUID();
    final UUID joinerId = UUID.randomUUID();
    final UUID meetupId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MeetupAccess access = new MeetupAccess(config, betaAccess, repository);
        MeetupNotifier notifier = new MeetupNotifier(users, renderer, notifications,
                org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class));
        MeetupResponseAssembler assembler = new MeetupResponseAssembler(repository, users);
        service = new MeetupServiceImpl(repository, access, config, geoService, userBlocks, moderation, notifier,
                assembler, meetupChat);
        service.clock = Clock.fixed(NOW, ZoneOffset.UTC);

        when(config.getBoolean(BusinessConfigKey.MEETUP_ENABLED)).thenReturn(true);
        when(config.getInt(any())).thenAnswer(invocation -> ((BusinessConfigKey) invocation.getArgument(0)).defaultInt());
        Ward ward = new Ward();
        ward.setCode("00004");
        ward.setProvinceCode("01");
        ward.setFullName("Phường Tây Hồ");
        ward.setProvinceName("Hà Nội");
        when(geoService.resolveWard(any(), any())).thenReturn(Optional.of(ward));
        when(repository.findBirthYear(hostId)).thenReturn(Optional.of(1995));
        when(repository.findBirthYear(joinerId)).thenReturn(Optional.of(1998));
        when(renderer.render(any(), anyMap(), any())).thenReturn(new NotificationMessage("title", "body"));
        when(users.findById(any())).thenReturn(Optional.empty());
        when(repository.lock(any())).thenReturn(true);
        when(repository.updateContent(any(), any(Long.class))).thenReturn(true);
        when(repository.withdraw(any(), any(), any())).thenReturn(true);
        when(repository.cancel(any(), any(), any(), any())).thenReturn(true);
        when(repository.updateParticipantStatus(any(), any(), any(), any(), any())).thenReturn(true);
    }

    static int code(Throwable throwable) {
        return ((BusinessException) throwable).getError().getCode();
    }

    MeetupUpsertRequest.MeetupUpsertRequestBuilder request() {
        return MeetupUpsertRequest.builder()
                .title("chạy bộ quanh hồ Tây")
                .category(MeetupCategory.OUTDOOR_ACTIVE)
                .locationMode(MeetupLocationMode.AREA)
                .radiusM(1000)
                .latitude(LAT)
                .longitude(LNG)
                .meetupDate(TODAY)
                .timeMode(MeetupTimeMode.FLEXIBLE)
                .minAge(18)
                .joinPolicy(MeetupJoinPolicy.OPEN);
    }

    Meetup.MeetupBuilder meetup() {
        return Meetup.builder()
                .id(meetupId)
                .hostUserId(hostId)
                .title("chạy bộ quanh hồ Tây")
                .category(MeetupCategory.OUTDOOR_ACTIVE)
                .locationMode(MeetupLocationMode.EXACT)
                .latitude(LAT)
                .longitude(LNG)
                .displayLatitude(new BigDecimal("21.0510000"))
                .displayLongitude(new BigDecimal("105.8210000"))
                .placeName("Công viên nước Hồ Tây")
                .address("614 Lạc Long Quân")
                .meetupDate(TODAY)
                .timeMode(MeetupTimeMode.FLEXIBLE)
                .visibleUntil(NOW_UTC.plusHours(5))
                .minAge(18)
                .joinPolicy(MeetupJoinPolicy.OPEN)
                .status(MeetupStatus.APPROVED)
                .submittedAt(NOW_UTC.minusHours(2))
                .resubmitCount(0)
                .revision(1)
                .dataVersion(3L);
    }

    void stored(Meetup meetup) {
        when(repository.findById(meetup.getId())).thenReturn(Optional.of(meetup));
    }

    void row(UUID userId, MeetupParticipantStatus status) {
        when(repository.findParticipant(meetupId, userId)).thenReturn(Optional.of(MeetupParticipant.builder()
                .meetupId(meetupId).userId(userId).status(status).requestedAt(NOW_UTC.minusHours(1)).build()));
    }

    void verifyNotified(UUID recipient, NotificationType type) {
        verify(notifications).createNotification(eq(recipient), isNull(), eq(type), any(), any(), anyMap(), any());
    }

    // --- gate and context -------------------------------------------------------------

    @Nested
    @DisplayName("feature gate")
    class Gate {

        @Test
        void everyUserEndpointIsClosedWhileTheFlagIsOffForNonBetaUsers() {
            when(config.getBoolean(BusinessConfigKey.MEETUP_ENABLED)).thenReturn(false);

            assertThatThrownBy(() -> service.create(hostId, request().build()))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_DISABLED));
            assertThatThrownBy(() -> service.discover(hostId, LAT, LNG, 30, null, 0, 20))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_DISABLED));
            assertThatThrownBy(() -> service.join(joinerId, meetupId, null))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_DISABLED));
            verify(repository, never()).insert(any());
        }

        @Test
        void betaUsersPassWhileTheFlagIsOff() {
            when(config.getBoolean(BusinessConfigKey.MEETUP_ENABLED)).thenReturn(false);
            when(betaAccess.isBetaUser(hostId)).thenReturn(true);

            MeetupContextResponse context = service.context(hostId);

            assertThat(context.isEnabled()).isTrue();
            assertThat(context.isCanHost()).isTrue();
            assertThat(context.getToday()).isEqualTo(TODAY);
            assertThat(context.getAreaRadiiM()).containsExactly(300, 1000, 3000);
            assertThat(context.getMaxDaysAhead()).isEqualTo(3);
        }

        @Test
        void contextNamesWhyTheUserCannotHost() {
            when(repository.findBirthYear(hostId)).thenReturn(Optional.empty());
            assertThat(service.context(hostId).getCannotHostReason())
                    .isEqualTo(MeetupCannotHostReason.BIRTH_YEAR_REQUIRED);

            when(repository.findBirthYear(hostId)).thenReturn(Optional.of(2010));
            assertThat(service.context(hostId).getCannotHostReason()).isEqualTo(MeetupCannotHostReason.UNDERAGE);

            when(repository.findBirthYear(hostId)).thenReturn(Optional.of(1995));
            when(repository.countActiveByHost(eq(hostId), any(), isNull())).thenReturn(2L);
            MeetupContextResponse limited = service.context(hostId);
            assertThat(limited.getCannotHostReason()).isEqualTo(MeetupCannotHostReason.HOST_LIMIT);
            assertThat(limited.isCanHost()).isFalse();
        }

        @Test
        void aDisabledContextHasNoReasonAndCannotHost() {
            when(config.getBoolean(BusinessConfigKey.MEETUP_ENABLED)).thenReturn(false);

            MeetupContextResponse context = service.context(hostId);

            assertThat(context.isEnabled()).isFalse();
            assertThat(context.isCanHost()).isFalse();
            assertThat(context.getCannotHostReason()).isNull();
        }
    }

    // --- create ------------------------------------------------------------------------

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        void aValidMeetupIsStoredPendingReviewWithAnOffsetDisplayPoint() {
            ArgumentCaptor<Meetup> captor = ArgumentCaptor.forClass(Meetup.class);

            MeetupDetailResponse detail = service.create(hostId, request().description("  ").build());

            verify(repository).insert(captor.capture());
            Meetup stored = captor.getValue();
            assertThat(stored.getStatus()).isEqualTo(MeetupStatus.PENDING_REVIEW);
            assertThat(stored.getHostUserId()).isEqualTo(hostId);
            assertThat(stored.getDescription()).isNull();
            assertThat(stored.getRadiusM()).isEqualTo(1000);
            assertThat(stored.getStartsAt()).isNull();
            assertThat(stored.getVisibleUntil()).isEqualTo(LocalDateTime.of(2026, 10, 4, 16, 59, 59));
            assertThat(stored.getSubmittedAt()).isEqualTo(NOW_UTC);
            assertThat(stored.getRevision()).isEqualTo(1);
            assertThat(stored.getAreaLabel()).isEqualTo("Phường Tây Hồ, Hà Nội");
            assertThat(stored.getWardCode()).isEqualTo("00004");
            assertThat(stored.getProvinceCode()).isEqualTo("01");
            double offsetMeters = GeoDistance.between(LAT.doubleValue(), LNG.doubleValue(),
                    stored.getDisplayLatitude().doubleValue(), stored.getDisplayLongitude().doubleValue());
            assertThat(offsetMeters).isLessThanOrEqualTo(0.35 * 1000 + 1);
            assertThat(detail.getId()).isEqualTo(stored.getId());
            assertThat(detail.getViewerRole()).isEqualTo(MeetupViewerRole.HOST);
            assertThat(detail.getEffectiveStatus()).isEqualTo(MeetupEffectiveStatus.PENDING_REVIEW);
        }

        @Test
        void anExactMeetupDropsTheRadiusAndMovesAtMost150Metres() {
            ArgumentCaptor<Meetup> captor = ArgumentCaptor.forClass(Meetup.class);

            service.create(hostId, request().locationMode(MeetupLocationMode.EXACT).radiusM(3000)
                    .timeMode(MeetupTimeMode.EXACT).startTime("19:00").build());

            verify(repository).insert(captor.capture());
            Meetup stored = captor.getValue();
            assertThat(stored.getRadiusM()).isNull();
            assertThat(stored.getStartsAt()).isEqualTo(LocalDateTime.of(2026, 10, 4, 12, 0));
            assertThat(stored.getVisibleUntil()).isEqualTo(LocalDateTime.of(2026, 10, 4, 15, 0));
            assertThat(GeoDistance.between(LAT.doubleValue(), LNG.doubleValue(),
                    stored.getDisplayLatitude().doubleValue(), stored.getDisplayLongitude().doubleValue()))
                    .isLessThanOrEqualTo(151);
        }

        @Test
        void hostingNeedsABirthYearAndAnAdult() {
            when(repository.findBirthYear(hostId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.create(hostId, request().build()))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_BIRTH_YEAR_REQUIRED));

            when(repository.findBirthYear(hostId)).thenReturn(Optional.of(2009));
            assertThatThrownBy(() -> service.create(hostId, request().build()))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_UNDERAGE));
            verify(repository, never()).insert(any());
        }

        @Test
        void theAgeRangeMustBeValidAndIncludeTheHost() {
            // Host born 1995 is 31.
            assertThatThrownBy(() -> service.create(hostId, request().minAge(17).build()))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_INVALID_AGE_RANGE));
            assertThatThrownBy(() -> service.create(hostId, request().minAge(30).maxAge(25).build()))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_INVALID_AGE_RANGE));
            assertThatThrownBy(() -> service.create(hostId, request().minAge(18).maxAge(100).build()))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_INVALID_AGE_RANGE));
            assertThatThrownBy(() -> service.create(hostId, request().minAge(18).maxAge(25).build()))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_AGE_RANGE_EXCLUDES_HOST));
            assertThatThrownBy(() -> service.create(hostId, request().minAge(35).build()))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_AGE_RANGE_EXCLUDES_HOST));
            service.create(hostId, request().minAge(31).maxAge(31).build());
            verify(repository).insert(any());
        }

        @Test
        void anAreaRadiusMustBeOneOfTheThreeChoices() {
            assertThatThrownBy(() -> service.create(hostId, request().radiusM(500).build()))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_INVALID_LOCATION));
            assertThatThrownBy(() -> service.create(hostId, request().radiusM(null).build()))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_INVALID_LOCATION));
        }

        @Test
        void aPointOutsideEveryWardIsRefused() {
            when(geoService.resolveWard(any(), any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(hostId, request().build()))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_OUTSIDE_SERVICE_AREA));
        }

        @Test
        void aBadDayOrTimeIsRefused() {
            assertThatThrownBy(() -> service.create(hostId, request().meetupDate(TODAY.plusDays(4)).build()))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_INVALID_TIME));
            assertThatThrownBy(() -> service.create(hostId, request().timeMode(MeetupTimeMode.EXACT)
                    .startTime("10:15").build()))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_INVALID_TIME));
        }

        @Test
        void theActiveAndDailyCapsApply() {
            when(repository.countActiveByHost(eq(hostId), eq(NOW_UTC), isNull())).thenReturn(2L);
            assertThatThrownBy(() -> service.create(hostId, request().build()))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_HOST_LIMIT));

            when(repository.countActiveByHost(eq(hostId), eq(NOW_UTC), isNull())).thenReturn(1L);
            when(repository.countSubmittedSince(hostId, NOW_UTC.minusHours(24))).thenReturn(5L);
            assertThatThrownBy(() -> service.create(hostId, request().build()))
                    .satisfies(e -> {
                        assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_RATE_LIMITED);
                        assertThat(String.valueOf(code(e))).startsWith("429");
                    });
            verify(repository, never()).insert(any());
        }
    }

    // --- edit, withdraw, cancel --------------------------------------------------------

    @Nested
    @DisplayName("host edits")
    class HostEdits {

        @Test
        void aRejectedMeetupIsResubmittedWhileResubmitsAreLeft() {
            stored(meetup().status(MeetupStatus.REJECTED).resubmitCount(1).revision(2).build());
            ArgumentCaptor<Meetup> captor = ArgumentCaptor.forClass(Meetup.class);

            service.update(hostId, meetupId, request().expectedVersion(3L).build());

            verify(repository).updateContent(captor.capture(), eq(3L));
            Meetup saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(MeetupStatus.PENDING_REVIEW);
            assertThat(saved.getResubmitCount()).isEqualTo(2);
            assertThat(saved.getRevision()).isEqualTo(3);
            assertThat(saved.getSubmittedAt()).isEqualTo(NOW_UTC);
        }

        @Test
        void aPendingEditKeepsItsPlaceInTheQueue() {
            LocalDateTime submitted = NOW_UTC.minusHours(2);
            stored(meetup().status(MeetupStatus.PENDING_REVIEW).submittedAt(submitted).build());
            ArgumentCaptor<Meetup> captor = ArgumentCaptor.forClass(Meetup.class);

            service.update(hostId, meetupId, request().expectedVersion(3L).build());

            verify(repository).updateContent(captor.capture(), eq(3L));
            assertThat(captor.getValue().getSubmittedAt()).isEqualTo(submitted);
            assertThat(captor.getValue().getResubmitCount()).isZero();
            assertThat(captor.getValue().getRevision()).isEqualTo(2);
        }

        @Test
        void editRules() {
            stored(meetup().status(MeetupStatus.REJECTED).resubmitCount(2).build());
            assertThatThrownBy(() -> service.update(hostId, meetupId, request().expectedVersion(3L).build()))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_RESUBMIT_LIMIT));

            stored(meetup().status(MeetupStatus.APPROVED).build());
            assertThatThrownBy(() -> service.update(hostId, meetupId, request().expectedVersion(3L).build()))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_NOT_EDITABLE));

            stored(meetup().status(MeetupStatus.PENDING_REVIEW).build());
            assertThatThrownBy(() -> service.update(hostId, meetupId, request().build()))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.INVALID_PARAMETERS));
            assertThatThrownBy(() -> service.update(joinerId, meetupId, request().expectedVersion(3L).build()))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_NOT_FOUND));

            when(repository.updateContent(any(), eq(2L))).thenReturn(false);
            assertThatThrownBy(() -> service.update(hostId, meetupId, request().expectedVersion(2L).build()))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.ALREADY_PROCESSED));
        }

        @Test
        void onlyAPendingMeetupIsWithdrawn() {
            stored(meetup().status(MeetupStatus.APPROVED).build());
            assertThatThrownBy(() -> service.withdraw(hostId, meetupId))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_INVALID_STATE));

            stored(meetup().status(MeetupStatus.PENDING_REVIEW).build());
            service.withdraw(hostId, meetupId);
            verify(repository).withdraw(meetupId, hostId, NOW_UTC);
        }

        @Test
        void cancellingTellsEveryoneWhoJoinedOrAsked() {
            stored(meetup().build());
            when(repository.findParticipants(eq(meetupId), any(), anyInt())).thenReturn(List.of(
                    MeetupParticipant.builder().meetupId(meetupId).userId(joinerId)
                            .status(MeetupParticipantStatus.JOINED).build(),
                    MeetupParticipant.builder().meetupId(meetupId).userId(UUID.randomUUID())
                            .status(MeetupParticipantStatus.REQUESTED).build()));

            service.cancel(hostId, meetupId, "  Trời mưa  ");

            verify(repository).cancel(meetupId, hostId, "Trời mưa", NOW_UTC);
            verify(notifications, org.mockito.Mockito.times(2)).createNotification(any(), isNull(),
                    eq(NotificationType.MEETUP_CANCELLED), any(), any(), anyMap(), eq(hostId));
            verifyNotified(joinerId, NotificationType.MEETUP_CANCELLED);
        }

        @Test
        void cancelRules() {
            stored(meetup().visibleUntil(NOW_UTC.minusMinutes(1)).build());
            assertThatThrownBy(() -> service.cancel(hostId, meetupId, null))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_EXPIRED));

            stored(meetup().status(MeetupStatus.PENDING_REVIEW).build());
            assertThatThrownBy(() -> service.cancel(hostId, meetupId, null))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_INVALID_STATE));
        }
    }

    // --- join, leave, host decisions --------------------------------------------------

    @Nested
    @DisplayName("joining")
    class Joining {

        @Test
        void anOpenMeetupSeatsAtOnceAndTellsTheHost() {
            stored(meetup().build());
            ArgumentCaptor<MeetupParticipant> captor = ArgumentCaptor.forClass(MeetupParticipant.class);

            MeetupJoinResponse response = service.join(joinerId, meetupId, "hello");

            assertThat(response.getStatus()).isEqualTo(MeetupParticipantStatus.JOINED);
            verify(repository).lock(meetupId);
            verify(repository).upsertParticipant(captor.capture());
            assertThat(captor.getValue().getMessage()).isNull();
            assertThat(captor.getValue().getDecidedAt()).isEqualTo(NOW_UTC);
            verifyNotified(hostId, NotificationType.MEETUP_PARTICIPANT_JOINED);
        }

        @Test
        void anApprovalMeetupRecordsARequestWithItsMessage() {
            stored(meetup().joinPolicy(MeetupJoinPolicy.APPROVAL).build());
            ArgumentCaptor<MeetupParticipant> captor = ArgumentCaptor.forClass(MeetupParticipant.class);

            MeetupJoinResponse response = service.join(joinerId, meetupId, " Mình chạy 5 km ");

            assertThat(response.getStatus()).isEqualTo(MeetupParticipantStatus.REQUESTED);
            verify(repository).upsertParticipant(captor.capture());
            assertThat(captor.getValue().getMessage()).isEqualTo("Mình chạy 5 km");
            assertThat(captor.getValue().getDecidedAt()).isNull();
            verifyNotified(hostId, NotificationType.MEETUP_JOIN_REQUESTED);
        }

        @Test
        void aFullMeetupRefusesEvenARequest() {
            stored(meetup().maxParticipants(2).joinPolicy(MeetupJoinPolicy.APPROVAL).build());
            when(repository.countParticipantsWithStatus(meetupId, MeetupParticipantStatus.JOINED)).thenReturn(2L);

            assertThatThrownBy(() -> service.join(joinerId, meetupId, null))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_FULL));
            verify(repository, never()).upsertParticipant(any());
        }

        @Test
        void declinedOrRemovedPeopleCannotAskAgainButLeaversCan() {
            stored(meetup().build());
            row(joinerId, MeetupParticipantStatus.DECLINED);
            assertThatThrownBy(() -> service.join(joinerId, meetupId, null))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_JOIN_NOT_ALLOWED));
            row(joinerId, MeetupParticipantStatus.REMOVED);
            assertThatThrownBy(() -> service.join(joinerId, meetupId, null))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_JOIN_NOT_ALLOWED));

            row(joinerId, MeetupParticipantStatus.LEFT);
            assertThat(service.join(joinerId, meetupId, null).getStatus()).isEqualTo(MeetupParticipantStatus.JOINED);
        }

        @Test
        void joiningTwiceIsANoOp() {
            stored(meetup().build());
            row(joinerId, MeetupParticipantStatus.JOINED);

            assertThat(service.join(joinerId, meetupId, null).getStatus()).isEqualTo(MeetupParticipantStatus.JOINED);
            verify(repository, never()).upsertParticipant(any());
        }

        @Test
        void eligibilityRules() {
            // Joiner born 1998 is 28.
            stored(meetup().maxAge(25).build());
            assertThatThrownBy(() -> service.join(joinerId, meetupId, null))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_AGE_NOT_ALLOWED));

            stored(meetup().minAge(30).build());
            assertThatThrownBy(() -> service.join(joinerId, meetupId, null))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_AGE_NOT_ALLOWED));

            stored(meetup().build());
            assertThatThrownBy(() -> service.join(hostId, meetupId, null))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_JOIN_NOT_ALLOWED));

            stored(meetup().visibleUntil(NOW_UTC).build());
            assertThatThrownBy(() -> service.join(joinerId, meetupId, null))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_EXPIRED));

            when(repository.findBirthYear(joinerId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.join(joinerId, meetupId, null))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_BIRTH_YEAR_REQUIRED));
        }

        @Test
        void openRequestsAreCappedPerUser() {
            stored(meetup().joinPolicy(MeetupJoinPolicy.APPROVAL).build());
            when(repository.countOpenRequests(joinerId, NOW_UTC)).thenReturn(5L);

            assertThatThrownBy(() -> service.join(joinerId, meetupId, null))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_REQUEST_LIMIT));
        }

        @Test
        void hiddenMeetupsCannotBeJoined() {
            stored(meetup().status(MeetupStatus.PENDING_REVIEW).build());
            assertThatThrownBy(() -> service.join(joinerId, meetupId, null))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_NOT_FOUND));

            stored(meetup().build());
            when(userBlocks.blockedBetween(joinerId, hostId)).thenReturn(true);
            assertThatThrownBy(() -> service.join(joinerId, meetupId, null))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_NOT_FOUND));

            when(userBlocks.blockedBetween(joinerId, hostId)).thenReturn(false);
            when(moderation.isTakenDown(ModeratedContentType.MEETUP, meetupId)).thenReturn(true);
            assertThatThrownBy(() -> service.join(joinerId, meetupId, null))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_NOT_FOUND));
        }

        @Test
        void leavingFreesTheSeatOrWithdrawsTheRequest() {
            stored(meetup().build());
            row(joinerId, MeetupParticipantStatus.JOINED);
            assertThat(service.leave(joinerId, meetupId).getStatus()).isEqualTo(MeetupParticipantStatus.LEFT);

            row(joinerId, MeetupParticipantStatus.REQUESTED);
            assertThat(service.leave(joinerId, meetupId).getStatus()).isEqualTo(MeetupParticipantStatus.WITHDRAWN);

            row(joinerId, MeetupParticipantStatus.DECLINED);
            assertThatThrownBy(() -> service.leave(joinerId, meetupId))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_INVALID_STATE));
        }
    }

    @Nested
    @DisplayName("host decisions")
    class HostDecisions {

        @BeforeEach
        void approvalMeetup() {
            stored(meetup().joinPolicy(MeetupJoinPolicy.APPROVAL).maxParticipants(3).build());
        }

        @Test
        void acceptingSeatsTheRequesterAndTellsThem() {
            row(joinerId, MeetupParticipantStatus.REQUESTED);

            MeetupParticipantResponse response = service.accept(hostId, meetupId, joinerId);

            assertThat(response.getStatus()).isEqualTo(MeetupParticipantStatus.JOINED);
            verify(repository).lock(meetupId);
            verify(repository).updateParticipantStatus(meetupId, joinerId, MeetupParticipantStatus.REQUESTED,
                    MeetupParticipantStatus.JOINED, NOW_UTC);
            verifyNotified(joinerId, NotificationType.MEETUP_REQUEST_ACCEPTED);
        }

        @Test
        void acceptingRespectsCapacity() {
            row(joinerId, MeetupParticipantStatus.REQUESTED);
            when(repository.countParticipantsWithStatus(meetupId, MeetupParticipantStatus.JOINED)).thenReturn(3L);

            assertThatThrownBy(() -> service.accept(hostId, meetupId, joinerId))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_FULL));
        }

        @Test
        void decliningTellsTheRequester() {
            row(joinerId, MeetupParticipantStatus.REQUESTED);

            assertThat(service.decline(hostId, meetupId, joinerId).getStatus())
                    .isEqualTo(MeetupParticipantStatus.DECLINED);
            verifyNotified(joinerId, NotificationType.MEETUP_REQUEST_DECLINED);
        }

        @Test
        void removingTakesAJoinedSeatAway() {
            row(joinerId, MeetupParticipantStatus.JOINED);

            assertThat(service.removeParticipant(hostId, meetupId, joinerId).getStatus())
                    .isEqualTo(MeetupParticipantStatus.REMOVED);
        }

        @Test
        void decisionRules() {
            row(joinerId, MeetupParticipantStatus.JOINED);
            assertThatThrownBy(() -> service.accept(hostId, meetupId, joinerId))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_INVALID_STATE));
            assertThatThrownBy(() -> service.accept(joinerId, meetupId, joinerId))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_NOT_FOUND));
            UUID stranger = UUID.randomUUID();
            assertThatThrownBy(() -> service.decline(hostId, meetupId, stranger))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_PARTICIPANT_NOT_FOUND));
        }
    }

    // --- invitations --------------------------------------------------------------------

    @Nested
    @DisplayName("invitations")
    class Invitations {

        @BeforeEach
        void approvalMeetupWithAFollower() {
            stored(meetup().joinPolicy(MeetupJoinPolicy.APPROVAL).maxParticipants(3).build());
            when(repository.isInvitable(hostId, joinerId)).thenReturn(true);
        }

        @Test
        void theHostInvitesSomeoneTheyAreConnectedToAndTellsThem() {
            MeetupParticipantResponse response = service.invite(hostId, meetupId, joinerId);

            assertThat(response.getStatus()).isEqualTo(MeetupParticipantStatus.INVITED);
            ArgumentCaptor<MeetupParticipant> saved = ArgumentCaptor.forClass(MeetupParticipant.class);
            verify(repository).upsertParticipant(saved.capture());
            assertThat(saved.getValue().getStatus()).isEqualTo(MeetupParticipantStatus.INVITED);
            assertThat(saved.getValue().getDecidedAt()).isNull();
            verifyNotified(joinerId, NotificationType.MEETUP_INVITED);
            // An invitation is not a seat: the chat waits for the answer.
            verify(meetupChat, never()).onMembershipChanged(any());
        }

        @Test
        void onlyConnectedPeopleWithNoBlockCanBeInvited() {
            UUID stranger = UUID.randomUUID();
            assertThatThrownBy(() -> service.invite(hostId, meetupId, stranger))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_INVITE_NOT_ALLOWED));
            assertThatThrownBy(() -> service.invite(hostId, meetupId, hostId))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_INVITE_NOT_ALLOWED));
            assertThatThrownBy(() -> service.invite(joinerId, meetupId, hostId))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_NOT_FOUND));
            verify(repository, never()).upsertParticipant(any());
        }

        @Test
        void inviteRules() {
            row(joinerId, MeetupParticipantStatus.REQUESTED);
            assertThatThrownBy(() -> service.invite(hostId, meetupId, joinerId))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_INVALID_STATE));

            row(joinerId, MeetupParticipantStatus.INVITED);
            assertThat(service.invite(hostId, meetupId, joinerId).getStatus())
                    .isEqualTo(MeetupParticipantStatus.INVITED);
            verify(repository, never()).upsertParticipant(any());

            // The host's own invitation overrides an earlier decline or removal.
            row(joinerId, MeetupParticipantStatus.REMOVED);
            assertThat(service.invite(hostId, meetupId, joinerId).getStatus())
                    .isEqualTo(MeetupParticipantStatus.INVITED);

            when(repository.countParticipantsWithStatus(meetupId, MeetupParticipantStatus.JOINED)).thenReturn(3L);
            assertThatThrownBy(() -> service.invite(hostId, meetupId, joinerId))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_FULL));

            when(repository.countParticipantsWithStatus(meetupId, MeetupParticipantStatus.JOINED)).thenReturn(0L);
            when(repository.countParticipantsWithStatus(meetupId, MeetupParticipantStatus.INVITED))
                    .thenReturn((long) MeetupServiceImpl.MAX_PENDING_INVITES);
            assertThatThrownBy(() -> service.invite(hostId, meetupId, joinerId))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_INVITE_LIMIT));

            stored(meetup().status(MeetupStatus.PENDING_REVIEW).build());
            assertThatThrownBy(() -> service.invite(hostId, meetupId, joinerId))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_INVALID_STATE));
        }

        @Test
        void acceptingSeatsTheInviteeWithNoApprovalAndTellsTheHost() {
            row(joinerId, MeetupParticipantStatus.INVITED);

            MeetupJoinResponse response = service.join(joinerId, meetupId, "hi");

            assertThat(response.getStatus()).isEqualTo(MeetupParticipantStatus.JOINED);
            verify(repository).updateParticipantStatus(meetupId, joinerId, MeetupParticipantStatus.INVITED,
                    MeetupParticipantStatus.JOINED, NOW_UTC);
            verify(repository, never()).upsertParticipant(any());
            verify(meetupChat).onMembershipChanged(meetupId);
            verifyNotified(hostId, NotificationType.MEETUP_PARTICIPANT_JOINED);
        }

        @Test
        void acceptingStillChecksAgeAndSeats() {
            row(joinerId, MeetupParticipantStatus.INVITED);
            when(repository.countParticipantsWithStatus(meetupId, MeetupParticipantStatus.JOINED)).thenReturn(3L);
            assertThatThrownBy(() -> service.join(joinerId, meetupId, null))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_FULL));

            stored(meetup().joinPolicy(MeetupJoinPolicy.APPROVAL).minAge(40).build());
            assertThatThrownBy(() -> service.join(joinerId, meetupId, null))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_AGE_NOT_ALLOWED));
        }

        @Test
        void decliningOrTakingBackAnInvitationLeavesThePersonFreeToAsk() {
            row(joinerId, MeetupParticipantStatus.INVITED);
            assertThat(service.leave(joinerId, meetupId).getStatus()).isEqualTo(MeetupParticipantStatus.WITHDRAWN);

            assertThat(service.removeParticipant(hostId, meetupId, joinerId).getStatus())
                    .isEqualTo(MeetupParticipantStatus.WITHDRAWN);
            verify(meetupChat, never()).onMembershipChanged(any());
            assertThat(MeetupParticipantStatus.WITHDRAWN.canRejoin()).isTrue();
        }
    }

    // --- group chat ---------------------------------------------------------------------

    @Nested
    @DisplayName("group chat")
    class GroupChat {

        @Test
        void creatingAMeetupGivesItAGroupChat() {
            ArgumentCaptor<Meetup> captor = ArgumentCaptor.forClass(Meetup.class);

            service.create(hostId, request().build());

            verify(repository).insert(captor.capture());
            verify(meetupChat).onMeetupCreated(captor.getValue().getId());
        }

        @Test
        void joiningAnOpenMeetupJoinsTheChatButARequestDoesNot() {
            stored(meetup().build());
            service.join(joinerId, meetupId, null);
            verify(meetupChat).onMembershipChanged(meetupId);

            stored(meetup().joinPolicy(MeetupJoinPolicy.APPROVAL).build());
            org.mockito.Mockito.clearInvocations(meetupChat);
            service.join(joinerId, meetupId, null);
            verify(meetupChat, never()).onMembershipChanged(any());
        }

        @Test
        void acceptingAndRemovingMoveTheChatMembership() {
            stored(meetup().joinPolicy(MeetupJoinPolicy.APPROVAL).build());
            row(joinerId, MeetupParticipantStatus.REQUESTED);
            service.accept(hostId, meetupId, joinerId);
            verify(meetupChat).onMembershipChanged(meetupId);

            row(joinerId, MeetupParticipantStatus.JOINED);
            service.removeParticipant(hostId, meetupId, joinerId);
            verify(meetupChat, org.mockito.Mockito.times(2)).onMembershipChanged(meetupId);
        }

        @Test
        void decliningARequestLeavesTheChatAlone() {
            stored(meetup().joinPolicy(MeetupJoinPolicy.APPROVAL).build());
            row(joinerId, MeetupParticipantStatus.REQUESTED);

            service.decline(hostId, meetupId, joinerId);

            verify(meetupChat, never()).onMembershipChanged(any());
        }

        @Test
        void onlyLeavingASeatMovesTheChat() {
            stored(meetup().build());
            row(joinerId, MeetupParticipantStatus.REQUESTED);
            service.leave(joinerId, meetupId);
            verify(meetupChat, never()).onMembershipChanged(any());

            row(joinerId, MeetupParticipantStatus.JOINED);
            service.leave(joinerId, meetupId);
            verify(meetupChat).onMembershipChanged(meetupId);
        }

        @Test
        void cancellingOrWithdrawingClosesTheChat() {
            stored(meetup().build());
            service.cancel(hostId, meetupId, null);
            verify(meetupChat).onMeetupClosed(meetupId);

            stored(meetup().status(MeetupStatus.PENDING_REVIEW).build());
            service.withdraw(hostId, meetupId);
            verify(meetupChat, org.mockito.Mockito.times(2)).onMeetupClosed(meetupId);
        }
    }

    // --- visibility and reveal ----------------------------------------------------------

    @Nested
    @DisplayName("visibility")
    class Visibility {

        @Test
        void aMeetupNotYetApprovedIsTheHostsAlone() {
            stored(meetup().status(MeetupStatus.PENDING_REVIEW).build());

            assertThatThrownBy(() -> service.detail(joinerId, meetupId))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_NOT_FOUND));
            assertThat(service.detail(hostId, meetupId).getViewerRole()).isEqualTo(MeetupViewerRole.HOST);
        }

        @Test
        void aCancelledMeetupStaysReadableToItsParticipantsOnly() {
            stored(meetup().status(MeetupStatus.CANCELLED).build());
            row(joinerId, MeetupParticipantStatus.JOINED);

            assertThat(service.detail(joinerId, meetupId).getEffectiveStatus())
                    .isEqualTo(MeetupEffectiveStatus.CANCELLED);
            assertThatThrownBy(() -> service.detail(UUID.randomUUID(), meetupId))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_NOT_FOUND));
        }

        @Test
        void anAreaIsNeverRevealedToOthers() {
            stored(meetup().locationMode(MeetupLocationMode.AREA).radiusM(1000).build());
            row(joinerId, MeetupParticipantStatus.JOINED);

            MeetupDetailResponse detail = service.detail(joinerId, meetupId);

            assertThat(detail.isLocationHidden()).isTrue();
            assertThat(detail.getLatitude()).isEqualByComparingTo("21.051");
            assertThat(detail.getRadiusM()).isEqualTo(1000);
            assertThat(detail.getPlaceName()).isNull();
            assertThat(detail.getAddress()).isNull();
        }

        @Test
        void anExactSpotIsRevealedWhenOpenOrJoined() {
            stored(meetup().build());
            MeetupDetailResponse open = service.detail(joinerId, meetupId);
            assertThat(open.isLocationHidden()).isFalse();
            assertThat(open.getLatitude()).isEqualByComparingTo(LAT);
            assertThat(open.getPlaceName()).isEqualTo("Công viên nước Hồ Tây");
            assertThat(open.getAddress()).isEqualTo("614 Lạc Long Quân");

            stored(meetup().joinPolicy(MeetupJoinPolicy.APPROVAL).build());
            MeetupDetailResponse privateSpot = service.detail(joinerId, meetupId);
            assertThat(privateSpot.isLocationHidden()).isTrue();
            assertThat(privateSpot.getRadiusM()).isEqualTo(MeetupResponseAssembler.HIDDEN_EXACT_RADIUS_M);
            assertThat(privateSpot.getPlaceName()).isNull();
            assertThat(privateSpot.getAddress()).isNull();

            row(joinerId, MeetupParticipantStatus.JOINED);
            assertThat(service.detail(joinerId, meetupId).isLocationHidden()).isFalse();

            row(joinerId, MeetupParticipantStatus.REQUESTED);
            MeetupDetailResponse requested = service.detail(joinerId, meetupId);
            assertThat(requested.isLocationHidden()).isTrue();
            assertThat(requested.getViewerStatus()).isEqualTo(MeetupParticipantStatus.REQUESTED);
            assertThat(requested.getViewerRole()).isEqualTo(MeetupViewerRole.PARTICIPANT);
        }

        @Test
        void participantsOfAnApprovalMeetupAreHiddenFromStrangers() {
            stored(meetup().joinPolicy(MeetupJoinPolicy.APPROVAL).build());
            when(repository.countJoined(any())).thenReturn(Map.of(meetupId, 2L));

            MeetupDetailResponse detail = service.detail(joinerId, meetupId);
            assertThat(detail.isParticipantsVisible()).isFalse();
            assertThat(detail.getParticipantsPreview()).isEmpty();
            assertThat(detail.getJoinedCount()).isEqualTo(2);
            assertThat(detail.getParticipantCount()).isEqualTo(3);
            assertThat(detail.getPendingRequestCount()).as("host-only").isNull();
            assertThat(service.participants(joinerId, meetupId)).isEmpty();
            verify(repository, never()).findParticipants(eq(meetupId), any(), anyInt());
        }

        @Test
        void strangersSeeOnlyJoinedPeopleOfAnOpenMeetupWithoutMessages() {
            stored(meetup().build());
            when(repository.findParticipants(eq(meetupId), any(), anyInt())).thenReturn(List.of(
                    MeetupParticipant.builder().meetupId(meetupId).userId(UUID.randomUUID())
                            .status(MeetupParticipantStatus.JOINED).message("hi").build()));

            List<MeetupParticipantResponse> rows = service.participants(joinerId, meetupId);

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getMessage()).isNull();
            verify(repository).findParticipants(meetupId, List.of(MeetupParticipantStatus.JOINED),
                    MeetupServiceImpl.PARTICIPANT_LIST_LIMIT);
        }

        @Test
        void theHostSeesEveryRowAndTheMessages() {
            stored(meetup().joinPolicy(MeetupJoinPolicy.APPROVAL).build());
            when(repository.findParticipants(eq(meetupId), any(), anyInt())).thenReturn(List.of(
                    MeetupParticipant.builder().meetupId(meetupId).userId(joinerId)
                            .status(MeetupParticipantStatus.REQUESTED).message("hi").build()));

            List<MeetupParticipantResponse> rows = service.participants(hostId, meetupId);

            assertThat(rows.get(0).getMessage()).isEqualTo("hi");
            verify(repository).findParticipants(meetupId, List.of(), MeetupServiceImpl.PARTICIPANT_LIST_LIMIT);
        }
    }
}
