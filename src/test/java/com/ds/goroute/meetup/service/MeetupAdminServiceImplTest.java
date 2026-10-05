package com.ds.goroute.meetup.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.meetup.domain.Meetup;
import com.ds.goroute.meetup.domain.MeetupParticipant;
import com.ds.goroute.meetup.dto.AdminMeetupDetail;
import com.ds.goroute.meetup.dto.MeetupRejectRequest;
import com.ds.goroute.meetup.persistence.MeetupAdminQuery;
import com.ds.goroute.meetup.persistence.MeetupRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.notification.NotificationMessage;
import com.ds.goroute.service.notification.NotificationTemplateRenderer;
import com.ds.goroute.type.MeetupAdminSort;
import com.ds.goroute.type.MeetupCategory;
import com.ds.goroute.type.MeetupEffectiveStatus;
import com.ds.goroute.type.MeetupJoinPolicy;
import com.ds.goroute.type.MeetupLocationMode;
import com.ds.goroute.type.MeetupParticipantStatus;
import com.ds.goroute.type.MeetupRejectReason;
import com.ds.goroute.type.MeetupStatus;
import com.ds.goroute.type.MeetupTimeMode;
import com.ds.goroute.type.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MeetupAdminServiceImpl")
class MeetupAdminServiceImplTest {

    static final Instant NOW = Instant.parse("2026-10-04T03:00:00Z");
    static final LocalDateTime NOW_UTC = LocalDateTime.of(2026, 10, 4, 3, 0);

    @Mock MeetupRepository repository;
    @Mock UserRepository users;
    @Mock NotificationTemplateRenderer renderer;
    @Mock NotificationService notifications;
    @Mock MeetupChatService meetupChat;

    MeetupAdminServiceImpl service;

    final UUID adminId = UUID.randomUUID();
    final UUID hostId = UUID.randomUUID();
    final UUID joinerId = UUID.randomUUID();
    final UUID meetupId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MeetupResponseAssembler assembler = new MeetupResponseAssembler(repository, users);
        service = new MeetupAdminServiceImpl(repository, assembler, new MeetupNotifier(users, renderer, notifications,
                org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class)), meetupChat);
        service.clock = Clock.fixed(NOW, ZoneOffset.UTC);
        when(renderer.render(any(), anyMap(), any())).thenReturn(new NotificationMessage("title", "body"));
        when(users.findById(hostId)).thenReturn(Optional.of(User.builder().id(hostId).language("vi").build()));
        when(users.findByIds(any())).thenReturn(List.of(User.builder().id(hostId).fullName("Linh").build()));
        when(repository.review(any(), anyLong(), any(), any(), any(), any(), any())).thenReturn(true);
        when(repository.remove(any(), any(), any(), any())).thenReturn(true);
    }

    static int code(Throwable throwable) {
        return ((BusinessException) throwable).getError().getCode();
    }

    Meetup.MeetupBuilder pending() {
        return Meetup.builder()
                .id(meetupId)
                .hostUserId(hostId)
                .title("chạy bộ quanh hồ Tây")
                .category(MeetupCategory.OUTDOOR_ACTIVE)
                .locationMode(MeetupLocationMode.AREA)
                .radiusM(1000)
                .latitude(new BigDecimal("21.05"))
                .longitude(new BigDecimal("105.82"))
                .displayLatitude(new BigDecimal("21.051"))
                .displayLongitude(new BigDecimal("105.821"))
                .meetupDate(LocalDate.of(2026, 10, 4))
                .timeMode(MeetupTimeMode.FLEXIBLE)
                .visibleUntil(NOW_UTC.plusHours(5))
                .minAge(18)
                .joinPolicy(MeetupJoinPolicy.OPEN)
                .status(MeetupStatus.PENDING_REVIEW)
                .submittedAt(NOW_UTC.minusHours(1))
                .resubmitCount(0)
                .revision(1)
                .dataVersion(4L);
    }

    void stored(Meetup meetup) {
        when(repository.findById(meetupId)).thenReturn(Optional.of(meetup));
    }

    @Test
    void approvingPublishesAndTellsTheHost() {
        stored(pending().build());

        service.approve(adminId, meetupId, 4L);

        verify(repository).review(meetupId, 4L, MeetupStatus.APPROVED, null, null, adminId, NOW_UTC);
        verify(notifications).createNotification(eq(hostId), isNull(), eq(NotificationType.MEETUP_APPROVED),
                any(), any(), anyMap(), eq(adminId));
    }

    @Test
    void aReviewNeedsAPendingMeetupInsideItsWindowAtTheExpectedVersion() {
        stored(pending().visibleUntil(NOW_UTC).build());
        assertThatThrownBy(() -> service.approve(adminId, meetupId, 4L))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_EXPIRED));

        stored(pending().status(MeetupStatus.APPROVED).build());
        assertThatThrownBy(() -> service.approve(adminId, meetupId, 4L))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_INVALID_STATE));

        stored(pending().build());
        when(repository.review(eq(meetupId), eq(3L), any(), any(), any(), any(), any())).thenReturn(false);
        assertThatThrownBy(() -> service.approve(adminId, meetupId, 3L))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.ALREADY_PROCESSED));
        verify(notifications, never()).createNotification(any(), any(), any(), any(), any(), anyMap(), any());
    }

    @Test
    void rejectingWithOtherNeedsANote() {
        stored(pending().build());

        assertThatThrownBy(() -> service.reject(adminId, meetupId,
                new MeetupRejectRequest(MeetupRejectReason.OTHER, "  ", 4L)))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_REJECT_NOTE_REQUIRED));
        verify(repository, never()).review(any(), anyLong(), any(), any(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void rejectingTellsTheHostTheReasonInTheirLanguage() {
        stored(pending().build());
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);

        service.reject(adminId, meetupId,
                new MeetupRejectRequest(MeetupRejectReason.CONTACT_INFO, "Có số điện thoại", 4L));

        verify(repository).review(meetupId, 4L, MeetupStatus.REJECTED, MeetupRejectReason.CONTACT_INFO,
                "Có số điện thoại", adminId, NOW_UTC);
        verify(notifications).createNotification(eq(hostId), isNull(), eq(NotificationType.MEETUP_REJECTED),
                any(), any(), data.capture(), eq(adminId));
        assertThat(data.getValue())
                .containsEntry("reason", "Chứa thông tin liên hệ: Có số điện thoại")
                .containsEntry("meetupId", meetupId.toString())
                .containsEntry("deepLink", "/meetups/" + meetupId);
    }

    @Test
    void removingTellsTheHostWhyAndTheParticipantsThatItIsCancelled() {
        stored(pending().status(MeetupStatus.APPROVED).build());
        when(repository.findParticipants(eq(meetupId), any(), anyInt())).thenReturn(List.of(
                MeetupParticipant.builder().meetupId(meetupId).userId(joinerId)
                        .status(MeetupParticipantStatus.JOINED).build()));

        service.remove(adminId, meetupId, "Lừa đảo");

        verify(repository).remove(meetupId, "Lừa đảo", adminId, NOW_UTC);
        verify(meetupChat).onMeetupClosed(meetupId);
        verify(notifications).createNotification(eq(hostId), isNull(), eq(NotificationType.MEETUP_REMOVED),
                any(), any(), anyMap(), eq(adminId));
        verify(notifications).createNotification(eq(joinerId), isNull(), eq(NotificationType.MEETUP_CANCELLED),
                any(), any(), anyMap(), eq(adminId));
    }

    @Test
    void onlyAnApprovedMeetupIsRemoved() {
        stored(pending().build());

        assertThatThrownBy(() -> service.remove(adminId, meetupId, "x"))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_INVALID_STATE));
    }

    @Test
    void theDetailCarriesHostHistoryAndComputedStatus() {
        stored(pending().visibleUntil(NOW_UTC.minusMinutes(1)).build());
        when(repository.countHostStatuses(hostId)).thenReturn(Map.of("APPROVED", 2L, "REJECTED", 1L));
        when(repository.findBirthYear(hostId)).thenReturn(Optional.of(1995));
        when(repository.countReports(any())).thenReturn(Map.of(meetupId, 3L));

        AdminMeetupDetail detail = service.detail(meetupId);

        assertThat(detail.getEffectiveStatus()).isEqualTo(MeetupEffectiveStatus.EXPIRED);
        assertThat(detail.getHost().getName()).isEqualTo("Linh");
        assertThat(detail.getHost().getBirthYear()).isEqualTo(1995);
        assertThat(detail.getHost().getHostedTotal()).isEqualTo(3);
        assertThat(detail.getHost().getApprovedTotal()).isEqualTo(2);
        assertThat(detail.getReports().getCount()).isEqualTo(3);
        assertThat(detail.getLatitude()).isEqualByComparingTo("21.05");
        assertThat(detail.getDataVersion()).isEqualTo(4L);
    }

    @Test
    void theQueueDefaultsToDeadlineOrderAndEverythingElseToNewestSubmission() {
        ArgumentCaptor<MeetupAdminQuery> query = ArgumentCaptor.forClass(MeetupAdminQuery.class);

        service.list(List.of(MeetupStatus.PENDING_REVIEW), null, null, null, null, null, false, null, 0, 50);
        service.list(List.of(), null, " chạy ", null, null, null, true, null, 1, 50);
        service.list(List.of(MeetupStatus.PENDING_REVIEW), null, null, null, null, null, false,
                MeetupAdminSort.SUBMITTED_AT, 0, 50);

        verify(repository, org.mockito.Mockito.times(3)).countAdmin(query.capture());
        assertThat(query.getAllValues().get(0).getSort()).isEqualTo("DEADLINE");
        assertThat(query.getAllValues().get(1).getSort()).isEqualTo("SUBMITTED_AT");
        assertThat(query.getAllValues().get(1).getSearch()).isEqualTo("chạy");
        assertThat(query.getAllValues().get(1).getOffset()).isEqualTo(50);
        assertThat(query.getAllValues().get(1).isHidePast()).isTrue();
        assertThat(query.getAllValues().get(2).getSort()).isEqualTo("SUBMITTED_AT");
    }
}
