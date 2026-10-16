package com.ds.goroute.service.time;

import com.ds.goroute.mapper.UserMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserTimeZonesTest {

    private static final UUID USER = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T02:00:00Z"), ZoneOffset.UTC);

    @Test
    void withoutHeaderTheStoredZoneDecidesTheDay() {
        UserMapper mapper = mock(UserMapper.class);
        when(mapper.selectTimeZone(USER)).thenReturn("America/New_York");

        // 02:00 UTC on 14 Oct is 22:00 on 13 Oct in New York (EDT): that day began at 04:00 UTC.
        assertThat(new UserTimeZones(mapper).startOfTodayUtc(USER, CLOCK))
                .isEqualTo(LocalDateTime.of(2026, 10, 13, 4, 0));
    }

    @Test
    void anUnknownUserCountsInVietnam() {
        UserMapper mapper = mock(UserMapper.class);

        assertThat(new UserTimeZones(mapper).startOfTodayUtc(USER, CLOCK))
                .isEqualTo(LocalDateTime.of(2026, 10, 13, 17, 0));
    }

    @Test
    void rememberWritesOnlyWhenTheZoneChanges() {
        UserMapper mapper = mock(UserMapper.class);
        UserTimeZones zones = new UserTimeZones(mapper);

        zones.remember(USER, java.time.ZoneId.of("Asia/Tokyo"));
        zones.remember(USER, java.time.ZoneId.of("Asia/Tokyo"));
        zones.remember(USER, java.time.ZoneId.of("Europe/Paris"));

        verify(mapper, times(1)).updateTimeZone(USER, "Asia/Tokyo");
        verify(mapper, times(1)).updateTimeZone(USER, "Europe/Paris");
        verify(mapper, never()).selectTimeZone(USER);
    }
}
