package com.ds.goroute.meetup.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.type.MeetupTimeMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MeetupSchedule: Vietnamese days, UTC storage")
class MeetupScheduleTest {

    /** 10:00 in Vietnam on 4 October 2026. */
    private static final Instant NOW = Instant.parse("2026-10-04T03:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);

    private static MeetupSchedule.Window window(LocalDate date, MeetupTimeMode mode, String time) {
        return MeetupSchedule.window(date, mode, time, NOW, 3, 30, 3);
    }

    private static int code(Throwable throwable) {
        return ((BusinessException) throwable).getError().getCode();
    }

    @Test
    void todayIsTheVietnameseDateEvenWhenUtcIsStillYesterday() {
        // 23:30 UTC on the 3rd is 06:30 on the 4th in Vietnam.
        assertThat(MeetupSchedule.today(Instant.parse("2026-10-03T23:30:00Z"))).isEqualTo(TODAY);
    }

    @Test
    void flexibleStaysUntilLocalMidnightStoredInUtc() {
        MeetupSchedule.Window window = window(TODAY, MeetupTimeMode.FLEXIBLE, "19:00");

        assertThat(window.startsAt()).as("a flexible meetup has no start, the time is ignored").isNull();
        assertThat(window.visibleUntil()).isEqualTo(LocalDateTime.of(2026, 10, 4, 16, 59, 59));
    }

    @Test
    void theDayMustBeBetweenTodayAndMaxDaysAhead() {
        assertThat(window(TODAY.plusDays(3), MeetupTimeMode.FLEXIBLE, null).visibleUntil())
                .isEqualTo(LocalDateTime.of(2026, 10, 7, 16, 59, 59));

        assertThatThrownBy(() -> window(TODAY.minusDays(1), MeetupTimeMode.FLEXIBLE, null))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_INVALID_TIME));
        assertThatThrownBy(() -> window(TODAY.plusDays(4), MeetupTimeMode.FLEXIBLE, null))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_INVALID_TIME));
        assertThatThrownBy(() -> window(null, MeetupTimeMode.FLEXIBLE, null))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_INVALID_TIME));
    }

    @Test
    void anExactStartExactlyTheLeadTimeAwayIsAllowedAndOneMinuteLessIsNot() {
        // now = 10:00 local, lead 30 minutes.
        MeetupSchedule.Window window = window(TODAY, MeetupTimeMode.EXACT, "10:30");
        assertThat(window.startsAt()).isEqualTo(LocalDateTime.of(2026, 10, 4, 3, 30));
        assertThat(window.visibleUntil()).isEqualTo(LocalDateTime.of(2026, 10, 4, 6, 30));

        assertThatThrownBy(() -> window(TODAY, MeetupTimeMode.EXACT, "10:29"))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_INVALID_TIME));
    }

    @Test
    void anExactMeetupLeavesTheMapAtLocalMidnightAtTheLatest() {
        MeetupSchedule.Window window = window(TODAY, MeetupTimeMode.EXACT, "22:00");

        assertThat(window.startsAt()).isEqualTo(LocalDateTime.of(2026, 10, 4, 15, 0));
        // 22:00 + 3 h would be 01:00 tomorrow; capped at 23:59:59 local = 16:59:59 UTC.
        assertThat(window.visibleUntil()).isEqualTo(LocalDateTime.of(2026, 10, 4, 16, 59, 59));
    }

    @Test
    void anExactMeetupOnALaterDayIsNotLimitedByTheLeadTimeOfToday() {
        MeetupSchedule.Window window = window(TODAY.plusDays(1), MeetupTimeMode.EXACT, "07:00");

        assertThat(window.startsAt()).isEqualTo(LocalDateTime.of(2026, 10, 5, 0, 0));
        assertThat(window.visibleUntil()).isEqualTo(LocalDateTime.of(2026, 10, 5, 3, 0));
    }

    @Test
    void anExactMeetupNeedsAWellFormedTime() {
        assertThatThrownBy(() -> window(TODAY, MeetupTimeMode.EXACT, null))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_INVALID_TIME));
        assertThatThrownBy(() -> window(TODAY, MeetupTimeMode.EXACT, "25:00"))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorConstant.MEETUP_INVALID_TIME));
    }

    @Test
    void ageIsTheVietnameseCalendarYearMinusTheBirthYear() {
        assertThat(MeetupSchedule.age(2008, NOW)).isEqualTo(18);
        // 31 Dec 2026 20:00 UTC is already 1 Jan 2027 in Vietnam.
        assertThat(MeetupSchedule.age(2008, Instant.parse("2026-12-31T20:00:00Z"))).isEqualTo(19);
    }
}
