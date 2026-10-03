package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.MapSearchUsageMapper;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.MapSearchOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MapSearchRateLimiter")
class MapSearchRateLimiterTest {

    // 2026-10-01T18:30Z is already 2026-10-02 01:30 in Vietnam.
    private static final Instant NOW = Instant.parse("2026-10-01T18:30:00Z");
    private static final LocalDate VIETNAM_TODAY = LocalDate.of(2026, 10, 2);

    @Mock private BusinessConfigService config;
    @Mock private MapSearchUsageMapper usageMapper;
    private MapSearchRateLimiter limiter;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        limiter = new MapSearchRateLimiter(config, usageMapper, Clock.fixed(NOW, ZoneOffset.UTC));
        when(config.getBoolean(BusinessConfigKey.MAP_SEARCH_RATE_LIMIT_ENABLED)).thenReturn(true);
        when(config.getBoolean(BusinessConfigKey.MAP_SEARCH_EXEMPT_ADMINS)).thenReturn(true);
        when(config.getInt(BusinessConfigKey.MAP_SEARCH_AUTOCOMPLETE_PER_MINUTE)).thenReturn(2);
        when(config.getInt(BusinessConfigKey.MAP_SEARCH_AUTOCOMPLETE_PER_DAY)).thenReturn(100);
        when(config.getInt(BusinessConfigKey.MAP_SEARCH_PLACE_DETAIL_PER_MINUTE)).thenReturn(5);
        when(config.getInt(BusinessConfigKey.MAP_SEARCH_PLACE_DETAIL_PER_DAY)).thenReturn(100);
        when(usageMapper.claimRequest(any(), any(), anyString(), anyInt())).thenReturn(1);
    }

    @Test
    @DisplayName("counts each allowed call against the Vietnam calendar day")
    void countsAgainstVietnamDay() {
        limiter.acquire(userId, false, MapSearchOperation.AUTOCOMPLETE);

        verify(usageMapper).claimRequest(userId, VIETNAM_TODAY, "AUTOCOMPLETE", 100);
    }

    @Test
    @DisplayName("refuses the call past the per-minute limit without spending the daily allowance")
    void refusesPastMinuteLimit() {
        limiter.acquire(userId, false, MapSearchOperation.AUTOCOMPLETE);
        limiter.acquire(userId, false, MapSearchOperation.AUTOCOMPLETE);

        assertThatThrownBy(() -> limiter.acquire(userId, false, MapSearchOperation.AUTOCOMPLETE))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getError().getCode())
                .isEqualTo(ErrorConstant.MAP_SEARCH_MINUTE_LIMIT_REACHED);
        verify(usageMapper, times(2)).claimRequest(any(), any(), anyString(), anyInt());
        verify(usageMapper).recordRejected(userId, VIETNAM_TODAY, "AUTOCOMPLETE");
    }

    @Test
    @DisplayName("keeps a separate minute window per operation and per user")
    void separateWindows() {
        limiter.acquire(userId, false, MapSearchOperation.AUTOCOMPLETE);
        limiter.acquire(userId, false, MapSearchOperation.AUTOCOMPLETE);

        assertThatCode(() -> {
            limiter.acquire(userId, false, MapSearchOperation.PLACE_DETAIL);
            limiter.acquire(UUID.randomUUID(), false, MapSearchOperation.AUTOCOMPLETE);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("refuses the call once today's allowance is used up")
    void refusesPastDailyLimit() {
        when(usageMapper.claimRequest(any(), any(), anyString(), anyInt())).thenReturn(0);

        assertThatThrownBy(() -> limiter.acquire(userId, false, MapSearchOperation.PLACE_DETAIL))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getError().getCode())
                .isEqualTo(ErrorConstant.MAP_SEARCH_DAILY_LIMIT_REACHED);
        verify(usageMapper).recordRejected(userId, VIETNAM_TODAY, "PLACE_DETAIL");
    }

    @Test
    @DisplayName("lets admins through while EXEMPT_ADMINS is on, and limits them when it is off")
    void adminExemption() {
        for (int call = 0; call < 5; call++) {
            limiter.acquire(userId, true, MapSearchOperation.AUTOCOMPLETE);
        }
        verify(usageMapper, never()).claimRequest(any(), any(), anyString(), anyInt());

        when(config.getBoolean(BusinessConfigKey.MAP_SEARCH_EXEMPT_ADMINS)).thenReturn(false);
        limiter.acquire(userId, true, MapSearchOperation.AUTOCOMPLETE);
        verify(usageMapper).claimRequest(userId, VIETNAM_TODAY, "AUTOCOMPLETE", 100);
    }

    @Test
    @DisplayName("does nothing while RATE_LIMIT_ENABLED is off")
    void disabled() {
        when(config.getBoolean(BusinessConfigKey.MAP_SEARCH_RATE_LIMIT_ENABLED)).thenReturn(false);

        for (int call = 0; call < 5; call++) {
            limiter.acquire(userId, false, MapSearchOperation.AUTOCOMPLETE);
        }
        verify(usageMapper, never()).claimRequest(any(), any(), anyString(), anyInt());
    }

    @Test
    @DisplayName("lets the call through when the counter table is unreachable")
    void failsOpenOnDatabaseError() {
        when(usageMapper.claimRequest(any(), any(), anyString(), anyInt()))
                .thenThrow(new DataAccessResourceFailureException("down"));

        assertThatCode(() -> limiter.acquire(userId, false, MapSearchOperation.AUTOCOMPLETE))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("purges counters older than the retention window")
    void purgesByRetention() {
        when(config.getInt(BusinessConfigKey.MAP_SEARCH_USAGE_RETENTION_DAYS)).thenReturn(90);
        when(usageMapper.deleteBefore(any())).thenReturn(7);

        assertThat(limiter.purgeExpiredUsage()).isEqualTo(7);
        verify(usageMapper).deleteBefore(eq(VIETNAM_TODAY.minusDays(90)));
    }
}
