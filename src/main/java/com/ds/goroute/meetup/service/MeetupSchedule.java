package com.ds.goroute.meetup.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.type.MeetupTimeMode;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * The time rules of a meetup (SPEC_V2 §2.3). Every "day" is a Vietnamese day; every stored time
 * is UTC. A FLEXIBLE meetup stays on the map until 23:59:59 local time of its day; an EXACT one
 * starts at least {@code minLeadMinutes} from now and stays {@code visibleHours} after its start,
 * but never past that same local midnight.
 */
public final class MeetupSchedule {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    private MeetupSchedule() {
    }

    /** {@code startsAt} is null for FLEXIBLE. Both values are UTC. */
    public record Window(LocalDateTime startsAt, LocalDateTime visibleUntil) {
    }

    public static LocalDate today(Instant now) {
        return LocalDate.ofInstant(now, ZONE);
    }

    public static LocalDateTime nowUtc(Instant now) {
        return LocalDateTime.ofInstant(now, ZoneOffset.UTC);
    }

    /** Age in whole calendar years as the Vietnamese calendar sees it: current year minus birth year. */
    public static int age(int birthYear, Instant now) {
        return today(now).getYear() - birthYear;
    }

    /**
     * Computes the window, or throws {@code MEETUP_INVALID_TIME} when the day is outside
     * today..today+{@code maxDaysAhead}, an EXACT time is missing or malformed, or it starts
     * sooner than {@code minLeadMinutes} from now (exactly the lead time is allowed).
     */
    public static Window window(LocalDate meetupDate, MeetupTimeMode timeMode, String startTime, Instant now,
                                int maxDaysAhead, int minLeadMinutes, int visibleHours) {
        LocalDate today = today(now);
        if (meetupDate == null || meetupDate.isBefore(today) || meetupDate.isAfter(today.plusDays(maxDaysAhead))) {
            throw invalid("The day must be between today and " + maxDaysAhead + " days ahead (Vietnam time)");
        }
        LocalDateTime endOfDay = toUtc(meetupDate, END_OF_DAY);
        LocalDateTime nowUtc = nowUtc(now);
        if (timeMode == MeetupTimeMode.FLEXIBLE) {
            if (!endOfDay.isAfter(nowUtc)) {
                throw invalid("Today is already over");
            }
            return new Window(null, endOfDay);
        }
        if (timeMode != MeetupTimeMode.EXACT || startTime == null || startTime.isBlank()) {
            throw invalid("An exact meetup needs a start time (HH:mm)");
        }
        LocalTime localStart;
        try {
            localStart = LocalTime.parse(startTime.trim());
        } catch (DateTimeException exception) {
            throw invalid("The start time must be HH:mm");
        }
        LocalDateTime startsAt = toUtc(meetupDate, localStart);
        if (startsAt.isBefore(nowUtc.plusMinutes(minLeadMinutes))) {
            throw invalid("The start time must be at least " + minLeadMinutes + " minutes from now");
        }
        LocalDateTime visibleUntil = startsAt.plusHours(visibleHours);
        if (visibleUntil.isAfter(endOfDay)) {
            visibleUntil = endOfDay;
        }
        return new Window(startsAt, visibleUntil);
    }

    /** A Vietnamese local date and time as a UTC timestamp. */
    public static LocalDateTime toUtc(LocalDate date, LocalTime time) {
        return LocalDateTime.ofInstant(date.atTime(time).atZone(ZONE).toInstant(), ZoneOffset.UTC);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorConstant.MEETUP_INVALID_TIME, message);
    }
}
