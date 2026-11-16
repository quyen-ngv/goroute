package com.ds.goroute.service.notification;

import com.ds.goroute.dto.response.CityWeatherResponse;
import com.ds.goroute.dto.response.DailyForecastResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripDestination;
import com.ds.goroute.repository.TripDestinationRepository;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.CityWeatherService;
import com.ds.goroute.type.BusinessConfigKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripDayWeatherAlertsTest {

    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    private static final LocalDate START = LocalDate.of(2026, 8, 20);

    @Mock
    private CityWeatherService cityWeatherService;
    @Mock
    private TripDestinationRepository tripDestinationRepository;
    @Mock
    private BusinessConfigService businessConfig;
    @Mock
    private BetaAccessService betaAccess;

    private TripDayWeatherAlerts alerts;

    @BeforeEach
    void setUp() {
        alerts = new TripDayWeatherAlerts(cityWeatherService, tripDestinationRepository, businessConfig, betaAccess);
        lenient().when(businessConfig.getInt(BusinessConfigKey.TRIP_WEATHER_ALERT_RAIN_PERCENT)).thenReturn(80);
    }

    @Test
    void heavyRainRaisesAnAlertNamedAfterTheAdvisory() {
        forecast(day(START, 40, 70.0));

        Map<String, Object> data = alerts.forecastData(BigDecimal.ONE, BigDecimal.ONE, START);

        assertThat(data)
                .containsEntry("weather", "Heavy rain")
                .containsEntry("weatherVi", "Mưa to")
                .containsEntry("weatherAlertCode", "HEAVY_RAIN")
                .containsEntry("date", START.toString());
    }

    @Test
    void likelyRainAtTheThresholdRaisesAnAlertWithoutAnAdvisory() {
        forecast(day(START, 80, 4.0));

        Map<String, Object> data = alerts.forecastData(BigDecimal.ONE, BigDecimal.ONE, START);

        assertThat(data)
                .containsEntry("rainChance", 80)
                .containsEntry("weatherVi", "Khả năng mưa 80%")
                .doesNotContainKey("weatherAlertCode");
    }

    @Test
    void aDryDayOrAMissingForecastRaisesNothing() {
        forecast(day(START, 79, 4.0));

        assertThat(alerts.forecastData(BigDecimal.ONE, BigDecimal.ONE, START)).isNull();
        assertThat(alerts.forecastData(BigDecimal.ONE, BigDecimal.ONE, START.plusDays(1))).isNull();
    }

    @Test
    void aWeatherOutageCostsOnlyTheAlert() {
        when(cityWeatherService.getCurrentWeather(any(BigDecimal.class), any(BigDecimal.class)))
                .thenThrow(new IllegalStateException("provider down"));

        assertThat(alerts.forecastData(BigDecimal.ONE, BigDecimal.ONE, START)).isNull();
    }

    @Test
    void asksTheWeatherOnlyForTheDayWhoseEveningIsDue() {
        Trip trip = trip();
        forecast(day(START.plusDays(1), 90, 0.0));
        ZonedDateTime dueAt = START.atTime(TripDayWeatherAlerts.SEND_TIME).atZone(BANGKOK);

        List<TripDayWeatherAlerts.DueAlert> due = alerts.due(trip, List.of(), BANGKOK, dueAt::equals);

        assertThat(due).singleElement().satisfies(alert -> {
            assertThat(alert.day()).isEqualTo(START.plusDays(1));
            assertThat(alert.dayNumber()).isEqualTo(2);
            assertThat(alert.sendAt()).isEqualTo(dueAt);
        });
    }

    @Test
    void nothingDueMeansNoWeatherLookup() {
        assertThat(alerts.due(trip(), List.of(), BANGKOK, sendAt -> false)).isEmpty();

        verifyNoInteractions(cityWeatherService, tripDestinationRepository);
    }

    @Test
    void readsTheWeatherAtTheDaysFirstTimedStopThenItsLegThenTheTrip() {
        Trip trip = trip();
        Activity late = stop(2, LocalTime.of(15, 0), "16.0");
        Activity early = stop(2, LocalTime.of(8, 0), "16.5");
        TripDestination leg = TripDestination.builder()
                .lat(new BigDecimal("21.0")).lng(new BigDecimal("105.8"))
                .startDate(START.plusDays(2)).endDate(START.plusDays(2)).build();

        assertThat(TripDayWeatherAlerts.weatherPoint(trip, List.of(late, early), List.of(leg), 2, START.plusDays(1)))
                .containsExactly(new BigDecimal("16.5"), new BigDecimal("108.0"));
        assertThat(TripDayWeatherAlerts.weatherPoint(trip, List.of(late, early), List.of(leg), 3, START.plusDays(2)))
                .containsExactly(new BigDecimal("21.0"), new BigDecimal("105.8"));
        assertThat(TripDayWeatherAlerts.weatherPoint(trip, List.of(), List.of(), 1, START))
                .containsExactly(new BigDecimal("13.7"), new BigDecimal("100.5"));
    }

    @Test
    void onlyBetaUsersGetTheAlertWhileItIsSwitchedOff() {
        UUID beta = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        when(businessConfig.getBoolean(BusinessConfigKey.TRIP_WEATHER_ALERT_ENABLED)).thenReturn(false);
        when(betaAccess.isBetaUser(beta)).thenReturn(true);
        when(betaAccess.isBetaUser(other)).thenReturn(false);

        assertThat(alerts.recipients(Set.of(beta, other))).containsExactly(beta);

        when(businessConfig.getBoolean(BusinessConfigKey.TRIP_WEATHER_ALERT_ENABLED)).thenReturn(true);
        assertThat(alerts.recipients(Set.of(beta, other))).containsExactlyInAnyOrder(beta, other);
    }

    private void forecast(DailyForecastResponse day) {
        when(cityWeatherService.getCurrentWeather(any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(CityWeatherResponse.builder().daily(List.of(day)).build());
    }

    private static DailyForecastResponse day(LocalDate date, int rainChance, double rainMm) {
        return DailyForecastResponse.builder()
                .date(date.toString())
                .weatherCode(61)
                .precipitationProbabilityPercent(rainChance)
                .precipitationSumMm(rainMm)
                .temperatureMinC(24.4)
                .temperatureMaxC(29.6)
                .build();
    }

    private static Activity stop(int dayNumber, LocalTime startTime, String lat) {
        return Activity.builder()
                .id(UUID.randomUUID())
                .dayNumber(dayNumber)
                .sortOrder(0)
                .startTime(startTime)
                .lat(new BigDecimal(lat))
                .lng(new BigDecimal("108.0"))
                .build();
    }

    private static Trip trip() {
        return Trip.builder()
                .id(UUID.randomUUID())
                .ownerId(UUID.randomUUID())
                .name("Bangkok")
                .startDate(START)
                .endDate(START.plusDays(2))
                .destinationLat(new BigDecimal("13.7"))
                .destinationLng(new BigDecimal("100.5"))
                .timezone("Asia/Bangkok")
                .build();
    }
}
