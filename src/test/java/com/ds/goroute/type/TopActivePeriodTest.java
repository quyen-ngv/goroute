package com.ds.goroute.type;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TopActivePeriodTest {

    // Thursday 2026-10-22 01:30 in Vietnam (UTC+7) is still Wednesday 18:30 UTC.
    private static final Instant THURSDAY_EARLY_VIETNAM = Instant.parse("2026-10-21T18:30:00Z");

    @Test
    void dayStartsAtVietnamMidnight() {
        assertThat(TopActivePeriod.DAY.startUtc(THURSDAY_EARLY_VIETNAM))
                .isEqualTo(LocalDateTime.parse("2026-10-21T17:00:00"));
    }

    @Test
    void weekStartsOnMonday() {
        assertThat(TopActivePeriod.WEEK.startUtc(THURSDAY_EARLY_VIETNAM))
                .isEqualTo(LocalDateTime.parse("2026-10-18T17:00:00"));
    }

    @Test
    void mondayIsItsOwnWeekStart() {
        Instant mondayNoonVietnam = Instant.parse("2026-10-19T05:00:00Z");
        assertThat(TopActivePeriod.WEEK.startUtc(mondayNoonVietnam))
                .isEqualTo(LocalDateTime.parse("2026-10-18T17:00:00"));
    }

    @Test
    void monthAndYearStartOnTheFirst() {
        assertThat(TopActivePeriod.MONTH.startUtc(THURSDAY_EARLY_VIETNAM))
                .isEqualTo(LocalDateTime.parse("2026-09-30T17:00:00"));
        assertThat(TopActivePeriod.YEAR.startUtc(THURSDAY_EARLY_VIETNAM))
                .isEqualTo(LocalDateTime.parse("2025-12-31T17:00:00"));
    }
}
