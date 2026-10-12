package com.ds.goroute.service.assistant;

import com.ds.goroute.dto.response.CityWeatherResponse;
import com.ds.goroute.dto.response.DailyForecastResponse;
import com.ds.goroute.dto.response.FoodSummaryResponse;
import com.ds.goroute.dto.response.PlaceResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.AssistantTurn;
import com.ds.goroute.entity.LocationImage;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripDestination;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.localevent.dto.LocalEventSummaryResponse;
import com.ds.goroute.localevent.service.LocalEventPublicService;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.LocationImageRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.service.CityWeatherService;
import com.ds.goroute.service.FoodService;
import com.ds.goroute.service.PlaceService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.PlaceVisibilityStatus;
import com.ds.goroute.utils.PlaceAiFacts;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The tools Pengoo may call while answering, executed with the asker's permissions.
 *
 * <p>Every answer is deliberately small (a handful of rows, short fields): tool output is sent
 * back to the model on every later round of the same turn. Places are always kept inside the
 * tourist area of the point being asked about, the same rule the trip planner uses, so a
 * question about Da Nang does not get Hoi An answers.
 *
 * <p>Errors are answers too: a bad id or a missing city comes back as {@code {"error": ...}} so
 * the model can recover in its next step instead of the whole turn failing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantToolService {

    static final int MAX_PLACES = 8;
    static final double DEFAULT_RADIUS_KM = 15;
    static final double NEAR_ACTIVITY_RADIUS_KM = 3;
    static final double MAX_RADIUS_KM = 30;
    private static final ZoneId LOCAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AssistantTurnService turns;
    private final TripRepository trips;
    private final ActivityRepository activities;
    private final PlaceRepository places;
    private final PlaceService placeService;
    private final LocationImageRepository areas;
    private final CityWeatherService weatherService;
    private final LocalEventPublicService eventService;
    private final FoodService foodService;
    private final TripAccessGuard tripAccessGuard;
    private final AssistantTripSnapshots tripSnapshots;
    private final ObjectMapper objectMapper;

    public Map<String, Object> execute(UUID turnId, String tool, Map<String, Object> args) {
        AssistantTurn turn = turns.requireRunning(turnId);
        Map<String, Object> input = args == null ? Map.of() : args;
        Trip trip = turn.getTripId() == null ? null : trips.findById(turn.getTripId()).orElse(null);
        try {
            return switch (tool) {
                case "search_places" -> searchPlaces(turn, trip, input);
                case "get_place_details" -> placeDetails(input);
                case "get_weather" -> weather(trip, input);
                case "search_events" -> events(trip, input);
                case "get_local_dishes" -> localDishes(trip, input);
                case "travel_time" -> travelTime(trip, input);
                case "list_my_trips" -> myTrips(turn);
                case "get_trip" -> tripDetails(turn, input);
                default -> error("Unknown tool " + tool);
            };
        } catch (BusinessException | IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    // ---------------------------------------------------------------- tools

    private Map<String, Object> searchPlaces(AssistantTurn turn, Trip trip, Map<String, Object> args) {
        Anchor anchor = anchor(trip, args);
        String query = text(args.get("query"));
        List<String> groups = groups(text(args.get("kind")));
        double radius = clamp(number(args.get("radius_km")),
                anchor.nearActivity() ? NEAR_ACTIVITY_RADIUS_KM : DEFAULT_RADIUS_KM, 0.5, MAX_RADIUS_KM);
        int limit = (int) clamp(number(args.get("limit")), 6, 1, MAX_PLACES);
        BigDecimal radiusKm = BigDecimal.valueOf(radius);
        List<Place> found;
        if (query != null) {
            List<PlaceResponse> hits = placeService.searchPlaces(query, anchor.lat(), anchor.lng(), radiusKm,
                    null, groups, null, 0, limit * 4);
            List<UUID> ids = hits.stream().map(PlaceResponse::getId).filter(Objects::nonNull).toList();
            Map<UUID, Place> byId = new LinkedHashMap<>();
            for (Place place : ids.isEmpty() ? List.<Place>of() : places.findByIds(ids)) byId.put(place.getId(), place);
            found = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
        } else {
            found = places.findActiveForAiInArea(anchor.lat(), anchor.lng(), radiusKm, anchor.area(), groups, limit * 4);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Place place : found) {
            if (rows.size() >= limit) break;
            if (!isPublic(place) || !inArea(place, anchor.area())) continue;
            rows.add(placeRow(place, anchor));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("near", anchor.label());
        out.put("radiusKm", radius);
        out.put("places", rows);
        if (rows.isEmpty()) out.put("note", "No catalogue place matched; do not invent one.");
        return out;
    }

    private Map<String, Object> placeDetails(Map<String, Object> args) {
        Place place = places.findById(requiredUuid(args.get("place_id"), "place_id"))
                .filter(this::isPublic)
                .orElseThrow(() -> new IllegalArgumentException("Place not found"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", place.getId());
        out.put("name", place.getTitle());
        out.put("kind", place.getPlaceGroup() == PlaceGroup.FOOD_AND_DRINK ? "food" : "sight");
        putIfPresent(out, "types", place.getSubTypes() == null ? null : List.of(place.getSubTypes()));
        putIfPresent(out, "address", place.getAddress());
        putIfPresent(out, "rating", place.getReviewRating());
        putIfPresent(out, "reviews", place.getReviewCount());
        putIfPresent(out, "price", PlaceAiFacts.priceLabel(place.getPriceRange(), place.getAttributes()));
        putIfPresent(out, "tags", emptyToNull(PlaceAiFacts.aboutTags(place.getAbout())));
        putIfPresent(out, "hours", weeklyHours(place.getOpenHours()));
        putIfPresent(out, "menu", emptyToNull(PlaceAiFacts.menuHighlights(place.getMenu(), 10)));
        putIfPresent(out, "description", truncate(place.getAiDescription(), 400));
        putIfPresent(out, "visitMinutes", place.getVisitDurationMinutes());
        putIfPresent(out, "phone", place.getPhone());
        return out;
    }

    private Map<String, Object> weather(Trip trip, Map<String, Object> args) {
        Anchor anchor = anchor(trip, args);
        CityWeatherResponse weather = weatherService.getCurrentWeather(anchor.lat(), anchor.lng());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("near", anchor.label());
        if (anchor.date() != null) out.put("askedDate", anchor.date());
        if (weather == null) return error("Weather is unavailable right now");
        Map<String, Object> now = new LinkedHashMap<>();
        putIfPresent(now, "tempC", weather.getTemperatureC());
        putIfPresent(now, "condition", weather.getCondition());
        out.put("now", now);
        List<Map<String, Object>> days = new ArrayList<>();
        for (DailyForecastResponse day : weather.getDaily() == null ? List.<DailyForecastResponse>of() : weather.getDaily()) {
            if (days.size() >= 7) break;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", day.getDate());
            putIfPresent(row, "minC", day.getTemperatureMinC());
            putIfPresent(row, "maxC", day.getTemperatureMaxC());
            putIfPresent(row, "condition", day.getCondition());
            putIfPresent(row, "rainChance", day.getPrecipitationProbabilityPercent());
            putIfPresent(row, "sunset", day.getSunset());
            days.add(row);
        }
        out.put("forecast", days);
        out.put("note", "Forecast covers about a week from today; say so when the asked date is further away.");
        return out;
    }

    private Map<String, Object> events(Trip trip, Map<String, Object> args) {
        Anchor anchor = anchor(trip, args);
        String from = null;
        String to = null;
        if (anchor.date() != null && !anchor.date().isBefore(LocalDate.now(LOCAL_ZONE))) {
            from = anchor.date().atStartOfDay(LOCAL_ZONE).toInstant().toString();
            to = anchor.date().plusDays(1).atStartOfDay(LOCAL_ZONE).toInstant().toString();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LocalEventSummaryResponse event : eventService.search(anchor.lat().doubleValue(), anchor.lng().doubleValue(),
                DEFAULT_RADIUS_KM, from, to, null, 0, 8).getItems()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", event.getId());
            row.put("title", event.getTitle());
            putIfPresent(row, "category", event.getCategory());
            putIfPresent(row, "startAt", event.getStartAt());
            putIfPresent(row, "venue", event.getVenueName());
            putIfPresent(row, "free", event.getIsFree());
            putIfPresent(row, "priceMin", event.getPriceMin());
            rows.add(row);
        }
        return Map.of("near", anchor.label(), "events", rows);
    }

    private Map<String, Object> localDishes(Trip trip, Map<String, Object> args) {
        Anchor anchor = anchor(trip, args);
        String slug = citySlug(anchor.area());
        if (slug == null) return error("No dish list for this area");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (FoodSummaryResponse food : foodService.listByCity(slug, 10, 0)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", food.getId());
            row.put("name", food.getName());
            putIfPresent(row, "description", truncate(food.getDescription(), 140));
            rows.add(row);
        }
        return Map.of("area", anchor.label(), "dishes", rows);
    }

    private Map<String, Object> travelTime(Trip trip, Map<String, Object> args) {
        BigDecimal[] from = point(trip, text(args.get("from")));
        BigDecimal[] to = point(trip, text(args.get("to")));
        double km = haversineKm(from[0], from[1], to[0], to[1]);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("straightLineKm", round(km, 1));
        out.put("walkMinutes", (int) Math.round(km / 4.0 * 60));
        out.put("rideMinutes", (int) Math.round(km / 20.0 * 60) + 7);
        out.put("note", "Estimate from straight-line distance; city traffic can add more.");
        return out;
    }

    private Map<String, Object> myTrips(AssistantTurn turn) {
        LocalDate today = LocalDate.now(LOCAL_ZONE);
        List<Map<String, Object>> rows = new ArrayList<>();
        trips.findByUserId(turn.getUserId()).stream()
                .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                .sorted(Comparator.comparing((Trip t) -> t.getEndDate() != null && t.getEndDate().isBefore(today))
                        .thenComparing(t -> t.getStartDate() == null ? LocalDate.MAX : t.getStartDate()))
                .limit(10)
                .forEach(t -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", t.getId());
                    row.put("name", t.getName());
                    putIfPresent(row, "destination", t.getDestination());
                    putIfPresent(row, "startDate", t.getStartDate());
                    putIfPresent(row, "endDate", t.getEndDate());
                    row.put("canEdit", tripAccessGuard.canEdit(t, turn.getUserId()));
                    rows.add(row);
                });
        return Map.of("trips", rows);
    }

    private Map<String, Object> tripDetails(AssistantTurn turn, Map<String, Object> args) {
        Trip trip = tripAccessGuard.requireAccess(requiredUuid(args.get("trip_id"), "trip_id"), turn.getUserId());
        Map<String, Object> out = new LinkedHashMap<>(tripSnapshots.snapshot(trip));
        out.put("canEdit", tripAccessGuard.canEdit(trip, turn.getUserId()));
        return out;
    }

    // ---------------------------------------------------------------- anchoring

    /** Where a question is about, in priority order: an activity, a named city, a trip day, the trip. */
    record Anchor(BigDecimal lat, BigDecimal lng, LocationImage area, String label, LocalDate date, boolean nearActivity) {
    }

    Anchor anchor(Trip trip, Map<String, Object> args) {
        UUID activityId = uuid(args.get("activity_id"));
        if (activityId != null && trip != null) {
            Activity activity = activities.findById(activityId).filter(a -> trip.getId().equals(a.getTripId()))
                    .orElseThrow(() -> new IllegalArgumentException("Activity not found in this trip"));
            if (activity.getLat() == null || activity.getLng() == null) {
                throw new IllegalArgumentException("That activity has no location");
            }
            LocalDate date = activity.getDayNumber() == null ? null
                    : AssistantTripSnapshots.dateOfDay(trip, activity.getDayNumber()).orElse(null);
            return new Anchor(activity.getLat(), activity.getLng(), areaFor(activity.getLat(), activity.getLng()),
                    activity.getName(), date, true);
        }
        String city = text(args.get("city"));
        if (city != null) {
            LocationImage area = areas.findBestMatch(normalize(city)).flatMap(found -> areas.findById(found.getId()))
                    .filter(found -> found.getLatitude() != null && found.getLongitude() != null)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown destination: " + city));
            return new Anchor(area.getLatitude(), area.getLongitude(), area, area.getFullAddress(), null, false);
        }
        if (trip == null) throw new IllegalArgumentException("Say which city (argument city), or pick one of the user's trips");
        Integer day = integer(args.get("day"));
        LocalDate date = day == null ? null : AssistantTripSnapshots.dateOfDay(trip, day).orElse(null);
        if (day != null) {
            Optional<Activity> located = activities.findByTripIdAndDayNumber(trip.getId(), day).stream()
                    .filter(a -> a.getLat() != null && a.getLng() != null)
                    .findFirst();
            if (located.isPresent()) {
                Activity first = located.get();
                return new Anchor(first.getLat(), first.getLng(), areaFor(first.getLat(), first.getLng()),
                        "day " + day, date, false);
            }
        }
        TripDestination destination = destinationFor(trip, date);
        if (destination != null) {
            return new Anchor(destination.getLat(), destination.getLng(), areaFor(destination.getLat(), destination.getLng()),
                    destination.getName(), date, false);
        }
        if (trip.getDestinationLat() == null || trip.getDestinationLng() == null) {
            throw new IllegalArgumentException("This trip has no location yet; ask for a city");
        }
        return new Anchor(trip.getDestinationLat(), trip.getDestinationLng(),
                areaFor(trip.getDestinationLat(), trip.getDestinationLng()), trip.getDestination(), date, false);
    }

    private TripDestination destinationFor(Trip trip, LocalDate date) {
        List<TripDestination> located = tripSnapshots.destinationsOf(trip).stream()
                .filter(d -> d.getLat() != null && d.getLng() != null)
                .toList();
        if (located.isEmpty()) return null;
        if (date != null) {
            for (TripDestination destination : located) {
                boolean afterStart = destination.getStartDate() == null || !date.isBefore(destination.getStartDate());
                boolean beforeEnd = destination.getEndDate() == null || !date.isAfter(destination.getEndDate());
                if (afterStart && beforeEnd) return destination;
            }
        }
        return located.get(0);
    }

    private LocationImage areaFor(BigDecimal lat, BigDecimal lng) {
        try {
            return areas.findAreaIdForPoint(lat, lng).flatMap(areas::findById).orElse(null);
        } catch (RuntimeException e) {
            log.debug("Assistant: no tourist area for {},{}: {}", lat, lng, e.getMessage());
            return null;
        }
    }

    private BigDecimal[] point(Trip trip, String id) {
        UUID uuid = uuid(id);
        if (uuid == null) throw new IllegalArgumentException("from/to must be an activity id or a place id");
        if (trip != null) {
            Optional<Activity> activity = activities.findById(uuid).filter(a -> trip.getId().equals(a.getTripId()));
            if (activity.isPresent() && activity.get().getLat() != null && activity.get().getLng() != null) {
                return new BigDecimal[]{activity.get().getLat(), activity.get().getLng()};
            }
        }
        Place place = places.findById(uuid).filter(p -> p.getLatitude() != null && p.getLongitude() != null)
                .orElseThrow(() -> new IllegalArgumentException("Unknown id " + id));
        return new BigDecimal[]{place.getLatitude(), place.getLongitude()};
    }

    // ---------------------------------------------------------------- rows

    private Map<String, Object> placeRow(Place place, Anchor anchor) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", place.getId());
        row.put("name", place.getTitle());
        boolean food = place.getPlaceGroup() == PlaceGroup.FOOD_AND_DRINK;
        row.put("kind", food ? "food" : "sight");
        if (place.getSubTypes() != null && place.getSubTypes().length > 0) {
            row.put("types", List.of(place.getSubTypes()).subList(0, Math.min(3, place.getSubTypes().length)));
        }
        putIfPresent(row, "rating", place.getReviewRating());
        putIfPresent(row, "reviews", place.getReviewCount());
        putIfPresent(row, "price", PlaceAiFacts.priceLabel(place.getPriceRange(), place.getAttributes()));
        putIfPresent(row, "tags", emptyToNull(PlaceAiFacts.aboutTags(place.getAbout())));
        putIfPresent(row, "hours", dayHours(place.getOpenHours(), anchor.date()));
        if (place.getLatitude() != null && place.getLongitude() != null) {
            row.put("distanceKm", round(haversineKm(anchor.lat(), anchor.lng(), place.getLatitude(), place.getLongitude()), 1));
        }
        if (food) putIfPresent(row, "menu", emptyToNull(PlaceAiFacts.menuHighlights(place.getMenu(), 4)));
        putIfPresent(row, "desc", truncate(place.getAiDescription(), 140));
        return row;
    }

    /** Hours on the asked day when there is one, otherwise "daily …" or the first listed day. */
    private String dayHours(String openHoursJson, LocalDate date) {
        Map<String, List<String>> hours = openHours(openHoursJson);
        if (hours.isEmpty()) return null;
        if (date != null) {
            List<String> day = hours.get(weekday(date.getDayOfWeek()));
            return day == null ? "closed or unknown that day" : String.join(", ", day);
        }
        Set<String> distinct = new LinkedHashSet<>();
        hours.values().forEach(values -> distinct.add(String.join(", ", values)));
        return distinct.size() == 1 ? "daily " + distinct.iterator().next() : truncate(String.join(" | ", distinct), 80);
    }

    private String weeklyHours(String openHoursJson) {
        Map<String, List<String>> hours = openHours(openHoursJson);
        if (hours.isEmpty()) return null;
        Map<String, List<String>> byValue = new LinkedHashMap<>();
        hours.forEach((day, values) -> byValue.computeIfAbsent(String.join(", ", values), key -> new ArrayList<>())
                .add(day.length() > 3 ? day.substring(0, 3) : day));
        List<String> parts = new ArrayList<>();
        byValue.forEach((value, days) -> parts.add(String.join(",", days) + " " + value));
        return truncate(String.join(" | ", parts), 300);
    }

    private Map<String, List<String>> openHours(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, List<String>> parsed = objectMapper.readValue(json, new TypeReference<Map<String, List<String>>>() {
            });
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String weekday(DayOfWeek day) {
        return day.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    // ---------------------------------------------------------------- helpers

    private boolean isPublic(Place place) {
        return place.getVisibilityStatus() == null || place.getVisibilityStatus() == PlaceVisibilityStatus.ACTIVE;
    }

    private static boolean inArea(Place place, LocationImage area) {
        return area == null || place.getLocationImageId() == null || area.getId().equals(place.getLocationImageId());
    }

    private static List<String> groups(String kind) {
        if (kind == null) return null;
        return switch (kind.toLowerCase(Locale.ROOT)) {
            case "food" -> List.of(PlaceGroup.FOOD_AND_DRINK.name());
            case "sight" -> List.of(PlaceGroup.ATTRACTIONS.name());
            default -> null;
        };
    }

    /** Dish lists are keyed by a city slug; only a few areas carry one, the rest use their name. */
    private static String citySlug(LocationImage area) {
        if (area == null) return null;
        if (area.getCitySlug() != null && !area.getCitySlug().isBlank()) return area.getCitySlug();
        String normalized = area.getNormalizedAddress() != null ? area.getNormalizedAddress() : normalize(area.getFullAddress());
        return normalized == null || normalized.isBlank() ? null : normalized.replace(" ", "");
    }

    static String normalize(String text) {
        if (text == null) return null;
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replaceAll("\\s+", " ").trim();
    }

    private static double haversineKm(BigDecimal aLat, BigDecimal aLng, BigDecimal bLat, BigDecimal bLng) {
        double dLat = Math.toRadians(bLat.doubleValue() - aLat.doubleValue());
        double dLng = Math.toRadians(bLng.doubleValue() - aLng.doubleValue());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(aLat.doubleValue()))
                * Math.cos(Math.toRadians(bLat.doubleValue())) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    private static double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private static double clamp(Double value, double fallback, double min, double max) {
        double v = value == null || value.isNaN() ? fallback : value;
        return Math.max(min, Math.min(max, v));
    }

    private static Double number(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return null;
        try {
            return Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer integer(Object value) {
        Double number = number(value);
        return number == null ? null : number.intValue();
    }

    private static String text(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static UUID uuid(Object value) {
        return AssistantProposalService.uuid(value);
    }

    private static UUID requiredUuid(Object value, String name) {
        UUID id = uuid(value);
        if (id == null) throw new IllegalArgumentException(name + " must be an id from an earlier tool result");
        return id;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.length() > max ? trimmed.substring(0, max) + "…" : trimmed;
    }

    private static <T> List<T> emptyToNull(List<T> values) {
        return values == null || values.isEmpty() ? null : values;
    }

    private static void putIfPresent(Map<String, Object> row, String key, Object value) {
        if (value != null) row.put(key, value);
    }

    private static Map<String, Object> error(String message) {
        return Map.of("error", message == null ? "Tool failed" : message);
    }
}
