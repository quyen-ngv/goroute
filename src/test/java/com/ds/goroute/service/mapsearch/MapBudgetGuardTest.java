package com.ds.goroute.service.mapsearch;

import com.ds.goroute.mapper.MapProviderUsageMapper;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.MapBillingApi;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MapBudgetGuard")
class MapBudgetGuardTest {

    private static final Instant NOW = Instant.parse("2026-10-11T08:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);
    private static final LocalDate MONTH = LocalDate.of(2026, 10, 1);

    @Mock private BusinessConfigService config;
    @Mock private MapProviderUsageMapper usage;
    @Mock private MapUsageNotifier notifier;
    private MapBudgetGuard guard;

    @BeforeEach
    void setUp() {
        guard = new MapBudgetGuard(config, usage, notifier, Clock.fixed(NOW, ZoneOffset.UTC));
        when(config.getBoolean(BusinessConfigKey.MAP_BUDGET_ENABLED)).thenReturn(true);
        when(config.getInt(BusinessConfigKey.MAP_BUDGET_SEARCH_MONTHLY_CAP)).thenReturn(1000);
        when(config.getInt(BusinessConfigKey.MAP_BUDGET_DAILY_CAP_PERCENT)).thenReturn(10);
        when(config.getInt(BusinessConfigKey.MAP_BUDGET_WARN_PERCENT)).thenReturn(80);
        when(usage.claim(eq("DAY"), any(), anyString(), anyLong())).thenReturn(1L);
        when(usage.claim(eq("MONTH"), any(), anyString(), anyLong())).thenReturn(1L);
        when(usage.recordRefused(anyString(), any(), anyString())).thenReturn(1L);
    }

    @Test
    @DisplayName("counts one call against today (10% of the month) and this month")
    void countsDayAndMonth() {
        assertThat(guard.tryAcquire(MapBillingApi.SEARCH).allowed()).isTrue();

        verify(usage).claim("DAY", TODAY, "SEARCH", 100);
        verify(usage).claim("MONTH", MONTH, "SEARCH", 1000);
    }

    @Test
    @DisplayName("a spent day refuses until tomorrow, UTC, and tells operators once")
    void spentDay() {
        when(usage.claim(eq("DAY"), any(), anyString(), anyLong())).thenReturn(null);

        MapBudgetGuard.Decision decision = guard.tryAcquire(MapBillingApi.SEARCH);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.resumesAt()).isEqualTo(Instant.parse("2026-10-12T00:00:00Z"));
        verify(usage, never()).claim(eq("MONTH"), any(), anyString(), anyLong());
        verify(notifier).budgetReached(MapBillingApi.SEARCH, Instant.parse("2026-10-12T00:00:00Z"));
    }

    @Test
    @DisplayName("a spent month gives today's unit back and refuses until next month")
    void spentMonth() {
        when(usage.claim(eq("MONTH"), any(), anyString(), anyLong())).thenReturn(null);
        when(usage.recordRefused(anyString(), any(), anyString())).thenReturn(7L);

        MapBudgetGuard.Decision decision = guard.tryAcquire(MapBillingApi.SEARCH);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.resumesAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
        verify(usage).release("DAY", TODAY, "SEARCH");
        verify(notifier, never()).budgetReached(any(), any());
    }

    @Test
    @DisplayName("warns operators exactly when the month reaches WARN_PERCENT")
    void warnsAtThreshold() {
        when(usage.claim(eq("MONTH"), any(), anyString(), anyLong())).thenReturn(799L, 800L, 801L);

        guard.tryAcquire(MapBillingApi.SEARCH);
        guard.tryAcquire(MapBillingApi.SEARCH);
        guard.tryAcquire(MapBillingApi.SEARCH);

        verify(notifier).budgetWarning(MapBillingApi.SEARCH, 800L, 1000L);
    }

    @Test
    @DisplayName("a cap of zero refuses without counting")
    void zeroCap() {
        when(config.getInt(BusinessConfigKey.MAP_BUDGET_SEARCH_MONTHLY_CAP)).thenReturn(0);

        assertThat(guard.tryAcquire(MapBillingApi.SEARCH).allowed()).isFalse();
        verify(usage, never()).claim(anyString(), any(), anyString(), anyLong());
    }

    @Test
    @DisplayName("switched off, or the table unreachable, lets every call through")
    void failsOpen() {
        when(config.getBoolean(BusinessConfigKey.MAP_BUDGET_ENABLED)).thenReturn(false);
        assertThat(guard.tryAcquire(MapBillingApi.SEARCH).allowed()).isTrue();
        verify(usage, never()).claim(anyString(), any(), anyString(), anyLong());

        when(config.getBoolean(BusinessConfigKey.MAP_BUDGET_ENABLED)).thenReturn(true);
        when(usage.claim(anyString(), any(), anyString(), anyLong()))
                .thenThrow(new DataAccessResourceFailureException("down"));
        assertThat(guard.tryAcquire(MapBillingApi.SEARCH).allowed()).isTrue();
    }
}
