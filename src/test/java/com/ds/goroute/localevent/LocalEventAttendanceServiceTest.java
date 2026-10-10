package com.ds.goroute.localevent;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.localevent.domain.LocalEvent;
import com.ds.goroute.localevent.dto.LocalEventAttendanceResponse;
import com.ds.goroute.localevent.persistence.LocalEventAttendeeRepository;
import com.ds.goroute.localevent.persistence.LocalEventRepository;
import com.ds.goroute.localevent.service.LocalEventAttendanceService;
import com.ds.goroute.localevent.service.LocalEventChatService;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LocalEventAttendanceService")
class LocalEventAttendanceServiceTest {

    static final Instant NOW = Instant.parse("2026-10-09T03:00:00Z");
    static final LocalDateTime NOW_UTC = LocalDateTime.of(2026, 10, 9, 3, 0);

    @Mock LocalEventRepository events;
    @Mock LocalEventAttendeeRepository attendees;
    @Mock LocalEventChatService chat;
    @Mock BusinessConfigService config;
    @Mock BetaAccessService betaAccess;
    @Mock UserRepository users;

    LocalEventAttendanceService service;

    final UUID userId = UUID.randomUUID();
    final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        service = new LocalEventAttendanceService(events, attendees, chat, config, betaAccess, users);
        var clock = LocalEventAttendanceService.class.getDeclaredField("clock");
        clock.setAccessible(true);
        clock.set(service, Clock.fixed(NOW, ZoneOffset.UTC));
        when(config.getBoolean(BusinessConfigKey.EVENT_ENABLED)).thenReturn(true);
        published(event().build());
    }

    LocalEvent.LocalEventBuilder event() {
        return LocalEvent.builder()
                .id(eventId)
                .title("Hà Nội Jazz Night")
                .eventStatus("SCHEDULED")
                .startAt(NOW_UTC.plusDays(1))
                .endAt(NOW_UTC.plusDays(1).plusHours(3))
                .isDeleted(false);
    }

    void published(LocalEvent event) {
        when(events.findPublishedById(eventId)).thenReturn(Optional.of(event));
        when(events.findById(eventId)).thenReturn(Optional.of(event));
    }

    static int code(Throwable throwable) {
        return ((BusinessException) throwable).getError().getCode();
    }

    @Test
    void joiningAddsThePersonAndPutsThemInTheChatOnce() {
        when(attendees.add(eventId, userId, NOW_UTC)).thenReturn(true);
        when(attendees.isGoing(eventId, userId)).thenReturn(true);
        when(attendees.count(eventId)).thenReturn(1L);
        when(attendees.findUserIds(eventId, 8)).thenReturn(List.of(userId));
        when(users.findByIds(any())).thenReturn(List.of(User.builder().id(userId).fullName("Lan").build()));

        LocalEventAttendanceResponse response = service.join(userId, eventId);

        assertThat(response.isGoing()).isTrue();
        assertThat(response.getGoingCount()).isEqualTo(1);
        assertThat(response.getAttendeesPreview()).extracting(LocalEventAttendanceResponse.Attendee::getName)
                .containsExactly("Lan");
        verify(chat).onJoined(eventId, userId);

        // Already going: nothing is announced twice.
        when(attendees.add(eventId, userId, NOW_UTC)).thenReturn(false);
        service.join(userId, eventId);
        verify(chat).onJoined(eventId, userId);
    }

    @Test
    void aCancelledOrFinishedEventCannotBeJoined() {
        published(event().eventStatus("CANCELLED").build());
        assertThatThrownBy(() -> service.join(userId, eventId))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.LOCAL_EVENT_NOT_JOINABLE));

        published(event().endAt(NOW_UTC).build());
        assertThatThrownBy(() -> service.join(userId, eventId))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.LOCAL_EVENT_NOT_JOINABLE));
        assertThat(service.attendance(userId, eventId).isJoinable()).isFalse();
        verify(attendees, never()).add(any(), any(), any());
    }

    @Test
    void anUnpublishedEventIsNotFound() {
        when(events.findPublishedById(eventId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.join(userId, eventId))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.LOCAL_EVENT_NOT_FOUND));
        assertThatThrownBy(() -> service.attendance(userId, eventId))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.LOCAL_EVENT_NOT_FOUND));
    }

    @Test
    void joiningIsClosedWhileTheFlagIsOffExceptForBetaUsers() {
        when(config.getBoolean(BusinessConfigKey.EVENT_ENABLED)).thenReturn(false);
        assertThatThrownBy(() -> service.join(userId, eventId))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.LOCAL_EVENT_JOIN_DISABLED));
        assertThatThrownBy(() -> service.conversation(userId, eventId))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.LOCAL_EVENT_JOIN_DISABLED));
        // The read still answers, so the app knows not to offer "Join".
        assertThat(service.attendance(userId, eventId).isEnabled()).isFalse();

        when(betaAccess.isBetaUser(userId)).thenReturn(true);
        when(attendees.add(eventId, userId, NOW_UTC)).thenReturn(true);
        service.join(userId, eventId);
        verify(chat).onJoined(eventId, userId);
    }

    @Test
    void leavingTakesThePersonOutOfTheChatEvenAfterTheEvent() {
        published(event().endAt(NOW_UTC.minusDays(1)).build());
        when(attendees.remove(eventId, userId)).thenReturn(true);

        assertThat(service.leave(userId, eventId).isGoing()).isFalse();
        verify(chat).onLeft(eventId, userId);

        when(attendees.remove(eventId, userId)).thenReturn(false);
        service.leave(userId, eventId);
        verify(chat).onLeft(eventId, userId);
    }

    @Test
    void onlyPeopleGoingOpenTheChat() {
        assertThatThrownBy(() -> service.conversation(userId, eventId))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.FORBIDDEN_ERROR));
        verify(chat, never()).open(any(), any());

        when(attendees.isGoing(eventId, userId)).thenReturn(true);
        service.conversation(userId, eventId);
        verify(chat).open(userId, eventId);
    }

    @Test
    void facesAreShownOnlyToPeopleGoing() {
        when(attendees.count(eventId)).thenReturn(5L);

        LocalEventAttendanceResponse response = service.attendance(userId, eventId);

        assertThat(response.getGoingCount()).isEqualTo(5);
        assertThat(response.getAttendeesPreview()).isEmpty();
        verify(attendees, never()).findUserIds(any(), anyInt());
    }
}
