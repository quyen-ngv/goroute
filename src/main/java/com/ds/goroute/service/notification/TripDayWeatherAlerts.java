package com.ds.goroute.service.notification;

import com.ds.goroute.dto.response.CityWeatherResponse;
import com.ds.goroute.dto.response.DailyForecastResponse;
import com.ds.goroute.dto.response.WeatherAlertResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripDestination;
import com.ds.goroute.repository.TripDestinationRepository;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.CityWeatherService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.utils.WeatherAdvisoryEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Picks the trip days whose forecast is worth a push the evening before, so the members
 * can rework the day while there is still time to.
 *
 * <p>A day is worth it when the forecast raises an advisory of level WARNING or DANGER for
 * it (heavy rain, storm-force wind, severe heat, thunderstorm — the same NCHMF bands the
 * weather sheet uses; high UV is left out, it does not change a plan) or when its rain
 * chance reaches {@link BusinessConfigKey#TRIP_WEATHER_ALERT_RAIN_PERCENT}. The forecast is
 * read where that day happens: its first located stop, else the leg of the trip dated that
 * day, else the trip's primary destination.
 *
 * <p>The weather is only asked once a send time is due, so the minute-by-minute itinerary
 * job costs no forecast lookups the rest of the day; lookups share the 30-minute grid-cell
 * cache of {@link CityWeatherService}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TripDayWeatherAlerts {

    /** Trip local time, the evening before the day. */
    static final LocalTime SEND_TIME = LocalTime.of(19, 0);

    private static final Set<String> SEVERE_LEVELS = Set.of("WARNING", "DANGER");
    private static final String HIGH_UV = "HIGH_UV";

    private final CityWeatherService cityWeatherService;
    private final TripDestinationRepository tripDestinationRepository;
    private final BusinessConfigService businessConfig;
    private final BetaAccessService betaAccess;

    /** One day's alert, ready to send at {@link #sendAt()}. */
    public record DueAlert(LocalDate day, int dayNumber, ZonedDateTime sendAt, Map<String, Object> data) {
    }

    /** The alerts of [trip] whose send time [isDue] accepts and whose forecast calls for one. */
    public List<DueAlert> due(Trip trip,
                              List<Activity> activities,
                              ZoneId zone,
                              Predicate<ZonedDateTime> isDue) {
        LocalDate start = trip.getStartDate();
        LocalDate end = trip.getEndDate();
        if (start == null || end == null || end.isBefore(start)) {
            return List.of();
        }

        List<DueAlert> alerts = new ArrayList<>();
        List<TripDestination> destinations = null;
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            ZonedDateTime sendAt = day.minusDays(1).atTime(SEND_TIME).atZone(zone);
            if (!isDue.test(sendAt)) {
                continue;
            }
            int dayNumber = (int) ChronoUnit.DAYS.between(start, day) + 1;
            if (destinations == null) {
                destinations = tripDestinationRepository.findByTripId(trip.getId());
            }
            BigDecimal[] point = weatherPoint(trip, activities, destinations, dayNumber, day);
            if (point == null) {
                continue;
            }
            Map<String, Object> data = forecastData(point[0], point[1], day);
            if (data != null) {
                alerts.add(new DueAlert(day, dayNumber, sendAt, data));
            }
        }
        return alerts;
    }

    /** The members who get the alert: all of them once it is switched on, beta users before. */
    public Set<UUID> recipients(Set<UUID> members) {
        if (businessConfig.getBoolean(BusinessConfigKey.TRIP_WEATHER_ALERT_ENABLED)) {
            return members;
        }
        Set<UUID> beta = new LinkedHashSet<>();
        for (UUID member : members) {
            if (betaAccess.isBetaUser(member)) {
                beta.add(member);
            }
        }
        return beta;
    }

    /** The day's weather for the template, or null when nothing in it calls for an alert. */
    Map<String, Object> forecastData(BigDecimal lat, BigDecimal lng, LocalDate day) {
        CityWeatherResponse weather;
        try {
            weather = cityWeatherService.getCurrentWeather(lat, lng);
        } catch (RuntimeException exception) {
            // An outage costs this alert, never the rest of the itinerary job.
            log.warn("Weather unavailable for trip day alert at {},{}: {}", lat, lng, exception.getMessage());
            return null;
        }
        DailyForecastResponse forecast = dayOf(weather, day);
        if (forecast == null) {
            return null;
        }

        WeatherAlertResponse advisory = WeatherAdvisoryEvaluator
                .evaluate(null, forecast.getWeatherCode(), forecast, null)
                .stream()
                .filter(alert -> SEVERE_LEVELS.contains(alert.getLevel()) && !HIGH_UV.equals(alert.getCode()))
                .findFirst()
                .orElse(null);
        Integer rainChance = forecast.getPrecipitationProbabilityPercent();
        int rainThreshold = businessConfig.getInt(BusinessConfigKey.TRIP_WEATHER_ALERT_RAIN_PERCENT);
        boolean likelyRain = rainChance != null && rainChance >= rainThreshold;
        if (advisory == null && !likelyRain) {
            return null;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", day.toString());
        if (advisory != null) {
            data.put("weather", advisory.getTitle());
            data.put("weatherVi", advisory.getTitleVi());
            data.put("weatherAlertCode", advisory.getCode());
        } else {
            data.put("weather", "Rain likely (" + rainChance + "%)");
            data.put("weatherVi", "Khả năng mưa " + rainChance + "%");
        }
        if (rainChance != null) {
            data.put("rainChance", rainChance);
        }
        putRounded(data, "temperatureMinC", forecast.getTemperatureMinC());
        putRounded(data, "temperatureMaxC", forecast.getTemperatureMaxC());
        return data;
    }

    private static DailyForecastResponse dayOf(CityWeatherResponse weather, LocalDate day) {
        if (weather == null || weather.getDaily() == null) {
            return null;
        }
        String key = day.toString();
        return weather.getDaily().stream()
                .filter(forecast -> key.equals(forecast.getDate()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Where [day] happens, as {lat, lng}: the first located stop of the day (timed stops in
     * clock order, then manual order), else the leg dated that day, else the primary leg,
     * else the trip's own destination. Null when none of them has coordinates.
     */
    static BigDecimal[] weatherPoint(Trip trip,
                                     List<Activity> activities,
                                     List<TripDestination> destinations,
                                     int dayNumber,
                                     LocalDate day) {
        Activity stop = activities.stream()
                .filter(activity -> Objects.equals(activity.getDayNumber(), dayNumber)
                        && activity.getLat() != null && activity.getLng() != null)
                .min(Comparator
                        .comparing(Activity::getStartTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Activity::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        if (stop != null) {
            return new BigDecimal[]{stop.getLat(), stop.getLng()};
        }

        List<TripDestination> located = destinations == null ? List.of() : destinations.stream()
                .filter(destination -> destination.getLat() != null && destination.getLng() != null)
                .toList();
        for (TripDestination destination : located) {
            LocalDate from = destination.getStartDate() != null ? destination.getStartDate() : destination.getEndDate();
            LocalDate to = destination.getEndDate() != null ? destination.getEndDate() : destination.getStartDate();
            if (from != null && !day.isBefore(from) && !day.isAfter(to)) {
                return new BigDecimal[]{destination.getLat(), destination.getLng()};
            }
        }
        TripDestination primary = located.stream()
                .filter(destination -> Boolean.TRUE.equals(destination.getIsPrimary()))
                .findFirst()
                .orElseGet(() -> located.stream()
                        .min(Comparator.comparing(TripDestination::getOrderIndex,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .orElse(null));
        if (primary != null) {
            return new BigDecimal[]{primary.getLat(), primary.getLng()};
        }
        if (trip.getDestinationLat() != null && trip.getDestinationLng() != null) {
            return new BigDecimal[]{trip.getDestinationLat(), trip.getDestinationLng()};
        }
        return null;
    }

    private static void putRounded(Map<String, Object> data, String key, Double value) {
        if (value != null) {
            data.put(key, Math.round(value));
        }
    }
}
