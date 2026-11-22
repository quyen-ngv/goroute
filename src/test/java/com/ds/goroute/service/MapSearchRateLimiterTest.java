package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.MapSearchUsageMapper;
import com.ds.goroute.service.mapsearch.MapUsageNotifier;
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
import java.util.Map;
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
    @Mock private MapUsageNotifier notifier;
    private MapSearchRateLimiter limiter;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        limiter = new MapSearchRateLimiter(config, usageMapper, notifier, Clock.fixed(NOW, ZoneOffset.UTC));
        when(config.getBoolean(BusinessConfigKey.MAP_SEARCH_RATE_LIMIT_ENABLED)).thenReturn(true);
        when(config.getBoolean(BusinessConfigKey.MAP_SEARCH_EXEMPT_ADMINS)).thenReturn(true);
        when(config.getInt(BusinessConfigKey.MAP_SEARCH_AUTOCOMPLETE_PER_MINUTE)).thenReturn(2);
        when(config.getInt(BusinessConfigKey.MAP_SEARCH_AUTOCOMPLETE_PER_DAY)).thenReturn(100);
        when(config.getInt(BusinessConfigKey.MAP_SEARCH_PLACE_DETAIL_PER_MINUTE)).thenReturn(5);
        when(config.getInt(BusinessConfigKey.MAP_SEARCH_PLACE_DETAIL_PER_DAY)).thenReturn(100);
        when(config.getInt(BusinessConfigKey.MAP_SEARCH_GEOCODE_PER_MINUTE)).thenReturn(5);
        when(config.getInt(BusinessConfigKey.MAP_SEARCH_GEOCODE_PER_DAY)).thenReturn(100);
        when(config.getInt(BusinessConfigKey.MAP_SEARCH_ROUTE_PER_MINUTE)).thenReturn(5);
        when(config.getInt(BusinessConfigKey.MAP_SEARCH_ROUTE_PER_DAY)).thenReturn(100);
        when(usageMapper.claimRequest(any(), any(), anyString(), anyInt())).thenReturn(1);
        when(usageMapper.recordRejected(any(), any(), anyString())).thenReturn(1L);
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

    @Test
    @DisplayName("a minute refusal says when the next minute starts")
    void minuteRefusalCarriesRetryTime() {
        limiter.acquire(userId, false, MapSearchOperation.AUTOCOMPLETE);
        limiter.acquire(userId, false, MapSearchOperation.AUTOCOMPLETE);

        assertThatThrownBy(() -> limiter.acquire(userId, false, MapSearchOperation.AUTOCOMPLETE))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> (Map<?, ?>) ((BusinessException) exception).getError().getData())
                .satisfies(detail -> {
                    assertThat(detail.get("scope")).isEqualTo("MINUTE");
                    assertThat(detail.get("operation")).isEqualTo("AUTOCOMPLETE");
                    assertThat(detail.get("resetAt")).isEqualTo("2026-10-01T18:31:00Z");
                    assertThat(detail.get("retryAfterSeconds")).isEqualTo(60L);
                });
        verify(notifier, never()).userReachedDailyLimit(any(), any(), any());
    }

    @Test
    @DisplayName("a daily refusal resets at Vietnam midnight and tells the user once")
    void dailyRefusalNotifiesOnce() {
        when(usageMapper.claimRequest(any(), any(), anyString(), anyInt())).thenReturn(0);

        assertThatThrownBy(() -> limiter.acquire(userId, false, MapSearchOperation.GEOCODE))
                .extracting(exception -> (Map<?, ?>) ((BusinessException) exception).getError().getData())
                .satisfies(detail -> {
                    assertThat(detail.get("scope")).isEqualTo("DAY");
                    // 2026-10-03 00:00 in Vietnam
                    assertThat(detail.get("resetAt")).isEqualTo("2026-10-02T17:00:00Z");
                });
        assertThatThrownBy(() -> limiter.acquire(userId, false, MapSearchOperation.GEOCODE))
                .isInstanceOf(BusinessException.class);

        verify(notifier, times(1)).userReachedDailyLimit(userId, MapSearchOperation.GEOCODE,
                Instant.parse("2026-10-02T17:00:00Z"));
    }

    @Test
    @DisplayName("operators hear about a user refused SPAM_ALERT_REFUSALS times in a day")
    void spamAlertAtThreshold() {
        when(usageMapper.claimRequest(any(), any(), anyString(), anyInt())).thenReturn(0);
        when(usageMapper.recordRejected(any(), any(), anyString())).thenReturn(49L, 50L, 51L);

        for (int call = 0; call < 3; call++) {
            assertThatThrownBy(() -> limiter.acquire(userId, false, MapSearchOperation.ROUTE))
                    .isInstanceOf(BusinessException.class);
        }

        verify(notifier, times(1)).suspectedSpam(userId, MapSearchOperation.ROUTE, 50L);
    }

    @Test
    @DisplayName("a failing notification never changes the refusal")
    void notificationFailureKeepsRefusal() {
        when(usageMapper.claimRequest(any(), any(), anyString(), anyInt())).thenReturn(0);
        org.mockito.Mockito.doThrow(new IllegalStateException("push down"))
                .when(notifier).userReachedDailyLimit(any(), any(), any());

        assertThatThrownBy(() -> limiter.acquire(userId, false, MapSearchOperation.AUTOCOMPLETE))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getError().getCode())
                .isEqualTo(ErrorConstant.MAP_SEARCH_DAILY_LIMIT_REACHED);
    }
}
