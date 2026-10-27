package com.ds.goroute.service.assistant;

import com.ds.goroute.dto.response.BudgetOverviewResponse;
import com.ds.goroute.dto.response.CityWeatherResponse;
import com.ds.goroute.dto.response.DailyForecastResponse;
import com.ds.goroute.dto.response.ExpenseResponse;
import com.ds.goroute.dto.response.FoodSummaryResponse;
import com.ds.goroute.dto.response.PlaceResponse;
import com.ds.goroute.dto.response.PublicTripResponse;
import com.ds.goroute.dto.response.UserCheckinResponse;
import com.ds.goroute.dto.response.UserReviewResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.AssistantTurn;
import com.ds.goroute.entity.LocationImage;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripDestination;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.localevent.dto.LocalEventSummaryResponse;
import com.ds.goroute.localevent.service.LocalEventPublicService;
import com.ds.goroute.meetup.dto.MeetupSummaryResponse;
import com.ds.goroute.meetup.service.MeetupService;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.LocationImageRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.service.CityWeatherService;
import com.ds.goroute.service.ExpenseService;
import com.ds.goroute.service.FoodService;
import com.ds.goroute.service.PlaceService;
import com.ds.goroute.service.ReviewService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.TripService;
import com.ds.goroute.service.UserCheckinService;
import com.ds.goroute.type.LocalEventCategory;
import com.ds.goroute.type.MeetupCategory;
import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.PlaceSearchSort;
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
import java.time.temporal.ChronoUnit;
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
    static final double EVENT_RADIUS_KM = 20;
    static final double PUBLIC_TRIP_RADIUS_KM = 50;
    static final int MAX_PUBLIC_TRIPS = 6;
    static final int MAX_EXPENSES = 40;
    static final int MAX_COMMUNITY_ROWS = 5;
    static final int MAX_TRIP_CHECKINS = 15;
    static final int MAX_GUIDE_CHARS = 1500;
    /** Guide section type -> the key the model reads. */
    private static final Map<String, String> GUIDE_KEYS = guideKeys();
    private static final ZoneId LOCAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AssistantTurnService turns;
    private final TripRepository trips;
    private final ActivityRepository activities;
    private final PlaceRepository places;
    private final PlaceService placeService;
    private final LocationImageRepository areas;
    private final CityWeatherService weatherService;
    private final LocalEventPublicService eventService;
    private final MeetupService meetupService;
    private final FoodService foodService;
    private final ExpenseService expenseService;
    private final TripAccessGuard tripAccessGuard;
    private final TripService tripService;
    private final UserCheckinService checkinService;
    private final ReviewService reviewService;
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
                case "search_meetups" -> meetups(turn, trip, input);
                case "get_local_dishes" -> localDishes(trip, input);
                case "travel_time" -> travelTime(trip, input);
                case "list_my_trips" -> myTrips(turn);
                case "get_trip" -> tripDetails(turn, input);
                case "get_budget" -> budget(turn, trip, input);
                case "list_expenses" -> expenses(turn, trip, input);
                case "search_public_trips" -> publicTrips(turn, trip, input);
                case "get_place_community" -> placeCommunity(turn, input);
                case "get_trip_checkins" -> tripCheckins(turn, trip, input);
                case "rank_trip_places" -> rankTripPlaces(turn, trip, input);
                case "explore_destinations" -> exploreDestinations();
                case "get_destination_guide" -> destinationGuide(input);
                default -> error("Unknown tool " + tool);
            };
        } catch (BusinessException | IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    // ---------------------------------------------------------------- tools

    /**
     * The catalogue search matches place <em>names</em> only, word by word. A question such as
     * "quán cà phê dễ gặp bạn mới" would match every "Quán …" restaurant, so the request is
     * reduced first: kind words become a sub-type filter, filler words are dropped, and whatever
     * is left (a dish, a brand, a name) searches names. When that finds too little the search
     * relaxes on its own — the kinds without the words, then the widest radius — and says so.
     */
    private Map<String, Object> searchPlaces(AssistantTurn turn, Trip trip, Map<String, Object> args) {
        Anchor anchor = anchor(trip, args);
        PlaceQuery request = PlaceQuery.of(text(args.get("query")), strings(args.get("types")), text(args.get("kind")));
        double radius = clamp(number(args.get("radius_km")),
                anchor.nearActivity() ? NEAR_ACTIVITY_RADIUS_KM : DEFAULT_RADIUS_KM, 0.5, MAX_RADIUS_KM);
        int limit = (int) clamp(number(args.get("limit")), 6, 1, MAX_PLACES);
        PlaceSearchSort sort = sortOf(text(args.get("sort")));

        List<String> relaxed = new ArrayList<>();
        Map<UUID, Place> found = new LinkedHashMap<>();
        // Rows whose name holds the asked words; the padding rows below are marked so the model
        // does not take a same-kind place for one that serves the asked dish.
        Set<UUID> byName = new LinkedHashSet<>();
        collect(found, findPlaces(anchor, request.words(), request, radius, sort, limit), anchor, limit);
        byName.addAll(found.keySet());
        if (found.size() < limit && request.words() != null && request.hasKinds()) {
            int before = found.size();
            collect(found, findPlaces(anchor, null, request, radius, sort, limit), anchor, limit);
            if (found.size() > before) relaxed.add("added places of the same kind whose name does not contain '" + request.words() + "'");
        }
        if (found.isEmpty() && radius < MAX_RADIUS_KM) {
            radius = MAX_RADIUS_KM;
            collect(found, findPlaces(anchor, request.words(), request, radius, sort, limit), anchor, limit);
            byName.addAll(found.keySet());
            if (found.isEmpty() && request.words() != null && request.hasKinds()) {
                collect(found, findPlaces(anchor, null, request, radius, sort, limit), anchor, limit);
            }
            if (!found.isEmpty()) relaxed.add("widened the radius to " + (int) MAX_RADIUS_KM + " km");
        }

        // Hours for the asked day, else today: "is it open now / this morning" needs no second call.
        LocalDate hoursOn = anchor.date() != null ? anchor.date()
                : LocalDate.now(trip == null ? LOCAL_ZONE : com.ds.goroute.service.time.TripTimeZones.zoneOf(trip));
        List<Map<String, Object>> rows = found.values().stream()
                .map(place -> placeRow(place, anchor, hoursOn, request.words() != null && !byName.contains(place.getId())))
                .toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("near", anchor.label());
        out.put("radiusKm", radius);
        out.put("hoursOn", hoursOn);
        Map<String, Object> searched = new LinkedHashMap<>();
        putIfPresent(searched, "nameWords", request.words());
        putIfPresent(searched, "types", request.subTypes());
        putIfPresent(searched, "kind", request.groups() == null ? null : request.groups().get(0));
        if (sort != null && request.words() == null) searched.put("sort", sort.name());
        out.put("searched", searched);
        if (!relaxed.isEmpty()) out.put("relaxed", relaxed);
        out.put("places", rows);
        if (rows.isEmpty()) {
            out.put("note", "No catalogue place matched. Try other types or shorter name words; "
                    + "do not invent a place. web_search (if offered) is the last resort.");
        }
        return out;
    }

    /**
     * Name words rank by relevance. Without them, an asked order wins: "popular" is how many
     * app travellers put the place in a trip or checked in there (never Google stars), "rating"
     * the review score. With neither words, kinds nor an order, the planner's quality ranking.
     */
    private List<Place> findPlaces(Anchor anchor, String words, PlaceQuery request, double radius,
                                   PlaceSearchSort requested, int limit) {
        BigDecimal radiusKm = BigDecimal.valueOf(radius);
        if (words == null && request.subTypes() == null && requested == null) {
            return places.findActiveForAiInArea(anchor.lat(), anchor.lng(), radiusKm, anchor.area(), request.groups(), limit * 4);
        }
        PlaceSearchSort sort = words != null ? PlaceSearchSort.RELEVANCE
                : requested != null ? requested : PlaceSearchSort.POPULARITY;
        List<PlaceResponse> hits = placeService.searchPlaces(words, anchor.lat(), anchor.lng(), radiusKm, null,
                request.groups(), request.subTypes(), null, sort, null, null, null, false, null, 0, limit * 4);
        List<UUID> ids = hits.stream().map(PlaceResponse::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return List.of();
        Map<UUID, Place> byId = new LinkedHashMap<>();
        for (Place place : places.findByIds(ids)) byId.put(place.getId(), place);
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private void collect(Map<UUID, Place> found, List<Place> candidates, Anchor anchor, int limit) {
        for (Place place : candidates) {
            if (found.size() >= limit) return;
            if (isPublic(place) && inArea(place, anchor.area())) found.putIfAbsent(place.getId(), place);
        }
    }

    private Map<String, Object> placeDetails(Map<String, Object> args) {
        Place place = places.findById(requiredUuid(args.get("place_id"), "place_id"))
                .filter(this::isPublic)
                .orElseThrow(() -> new IllegalArgumentException("Place not found"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", place.getId());
        out.put("name", place.getTitle());
        out.put("kind", kindOf(place));
        putIfPresent(out, "types", place.getSubTypes() == null ? null : List.of(place.getSubTypes()));
        putIfPresent(out, "address", place.getAddress());
        putIfPresent(out, "rating", place.getReviewRating());
        putIfPresent(out, "reviews", place.getReviewCount());
        putUsage(out, place);
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

    /**
     * Published events around the anchor between two dates (a trip day, or {@code from}/{@code to}),
     * optionally of some categories and with words in the title. When the asked window has
     * nothing, the next upcoming events nearby are returned instead and marked as such, so
     * "nothing on Sunday" can still offer what is on soon.
     */
    private Map<String, Object> events(Trip trip, Map<String, Object> args) {
        Anchor anchor = anchor(trip, args);
        ZoneId zone = trip == null ? LOCAL_ZONE : com.ds.goroute.service.time.TripTimeZones.zoneOf(trip);
        LocalDate today = LocalDate.now(zone);
        LocalDate from = firstNonNull(date(args.get("from")), anchor.date());
        LocalDate to = date(args.get("to"));
        if (from != null && from.isBefore(today)) from = today;
        if (from != null && (to == null || to.isBefore(from))) to = from;
        String categories = eventCategories(strings(args.get("categories")));
        String words = text(args.get("query"));

        List<Map<String, Object>> rows = findEvents(anchor, zone, from, to, categories, words);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("near", anchor.label());
        if (from != null) out.put("window", from + ".." + to);
        if (rows.isEmpty() && (from != null || categories != null || words != null)) {
            List<Map<String, Object>> upcoming = findEvents(anchor, zone, null, null, null, null);
            out.put("events", List.of());
            out.put("upcomingInstead", upcoming);
            out.put("note", "Nothing matched the asked dates/filters; 'upcomingInstead' lists the next events nearby. Say so.");
            return out;
        }
        out.put("events", rows);
        if (rows.isEmpty()) out.put("note", "No published event nearby in the next 60 days; do not invent one.");
        return out;
    }

    private List<Map<String, Object>> findEvents(Anchor anchor, ZoneId zone, LocalDate from, LocalDate to, String categories, String words) {
        String fromAt = from == null ? null : from.atStartOfDay(zone).toInstant().toString();
        String toAt = to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant().toString();
        String needle = words == null ? null : normalize(words);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LocalEventSummaryResponse event : eventService.search(anchor.lat().doubleValue(), anchor.lng().doubleValue(),
                EVENT_RADIUS_KM, fromAt, toAt, categories, 0, needle == null ? 8 : 40).getItems()) {
            if (rows.size() >= 8) break;
            if (needle != null && !matchesWords(normalize(event.getTitle()), needle)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", event.getId());
            row.put("title", event.getTitle());
            row.put("kind", "event");
            putIfPresent(row, "category", event.getCategory());
            putIfPresent(row, "startAt", event.getStartAt());
            putIfPresent(row, "endAt", event.getEndAt());
            putIfPresent(row, "venue", event.getVenueName());
            putIfPresent(row, "area", truncate(event.getAddress(), 80));
            putIfPresent(row, "free", event.getIsFree());
            putIfPresent(row, "priceMin", event.getPriceMin());
            rows.add(row);
        }
        return rows;
    }

    /** Any of the words (3+ letters) in the title; "language exchange" finds "Language Exchange Night". */
    private static boolean matchesWords(String title, String needle) {
        if (title == null) return false;
        for (String word : needle.split(" ")) {
            if (word.length() >= 3 && title.contains(word)) return true;
        }
        return false;
    }

    private static String eventCategories(List<String> codes) {
        if (codes == null) return null;
        List<String> known = new ArrayList<>();
        for (String code : codes) {
            try {
                known.add(LocalEventCategory.valueOf(code.trim().toUpperCase(Locale.ROOT)).name());
            } catch (IllegalArgumentException ignored) {
                // An unknown category is dropped rather than failing the search.
            }
        }
        return known.isEmpty() ? null : String.join(",", known);
    }

    /** Approved meetups (people inviting strangers to coffee, a walk, a game night) near the anchor. */
    private Map<String, Object> meetups(AssistantTurn turn, Trip trip, Map<String, Object> args) {
        Anchor anchor = anchor(trip, args);
        List<MeetupCategory> categories = new ArrayList<>();
        for (String code : Optional.ofNullable(strings(args.get("categories"))).orElse(List.of())) {
            try {
                categories.add(MeetupCategory.valueOf(code.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Unknown category: search without it.
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MeetupSummaryResponse meetup : meetupService.discover(turn.getUserId(), anchor.lat(), anchor.lng(),
                EVENT_RADIUS_KM, categories.isEmpty() ? null : categories, 0, 8).getItems()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", meetup.getId());
            row.put("title", meetup.getTitle());
            row.put("kind", "meetup");
            putIfPresent(row, "category", meetup.getCategory());
            putIfPresent(row, "area", meetup.getAreaLabel());
            putIfPresent(row, "date", meetup.getMeetupDate());
            putIfPresent(row, "startsAt", meetup.getStartsAt());
            putIfPresent(row, "joinPolicy", meetup.getJoinPolicy());
            row.put("people", meetup.getParticipantCount());
            putIfPresent(row, "maxPeople", meetup.getMaxParticipants());
            rows.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("near", anchor.label());
        out.put("meetups", rows);
        if (rows.isEmpty()) {
            out.put("note", "No open meetup nearby right now. The user can host one themselves in the app (Home > Hoạt động).");
        }
        return out;
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
            row.put("kind", "dish");
            row.put("citySlug", slug);
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

    /**
     * The trip's money so far, in the trip's currency: budget, spent, still planned (activity
     * estimates), by category, and the asker's own share. Any member may read these totals in the
     * app; per-person balances stay in the app.
     */
    private Map<String, Object> budget(AssistantTurn turn, Trip trip, Map<String, Object> args) {
        UUID tripId = trip != null ? trip.getId() : requiredUuid(args.get("trip_id"), "trip_id");
        BudgetOverviewResponse overview = expenseService.getBudgetOverview(tripId, turn.getUserId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("currency", overview.getCurrency());
        if (overview.getTotalBudget() != null && overview.getTotalBudget().signum() > 0) {
            out.put("budget", overview.getTotalBudget());
            out.put("remaining", overview.getRemaining());
            out.put("percentSpent", overview.getPercentageSpent());
        }
        out.put("spent", overview.getTotalSpent());
        putIfPresent(out, "plannedFromActivityEstimates", overview.getTotalEstimated());
        if (overview.getByCategory() != null && !overview.getByCategory().isEmpty()) {
            out.put("spentByCategory", overview.getByCategory());
        }
        putIfPresent(out, "askerShare", overview.getMyShare());
        putIfPresent(out, "askerPaidOrSettled", overview.getMyPaid());
        out.put("note", "Totals from expenses members logged in the app; who owes whom is on the trip's Budget tab.");
        return out;
    }

    /**
     * The trip's expenses, newest first, so a proposal can name one to change or remove: id, day,
     * amount, category, description, who paid, and who shares it (names only).
     */
    private Map<String, Object> expenses(AssistantTurn turn, Trip trip, Map<String, Object> args) {
        UUID tripId = trip != null ? trip.getId() : requiredUuid(args.get("trip_id"), "trip_id");
        List<ExpenseResponse> all = expenseService.getExpenses(tripId, null, turn.getUserId());
        String words = text(args.get("query"));
        String needle = words == null ? null : words.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ExpenseResponse expense : all.stream()
                .sorted(Comparator.comparing((ExpenseResponse e) -> e.getExpenseDate() == null
                        ? java.time.LocalDateTime.MIN : e.getExpenseDate()).reversed())
                .toList()) {
            if (needle != null && (expense.getDescription() == null
                    || !expense.getDescription().toLowerCase(Locale.ROOT).contains(needle))) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", expense.getId());
            if (expense.getExpenseDate() != null) row.put("date", expense.getExpenseDate().toLocalDate().toString());
            row.put("amount", expense.getAmount() == null ? null : expense.getAmount().stripTrailingZeros().toPlainString());
            row.put("currency", expense.getCurrency());
            row.put("category", expense.getCategory());
            putIfPresent(row, "description", expense.getDescription());
            if (expense.getPaidBy() != null) {
                putIfPresent(row, "paidBy", expense.getPaidBy().getFullName() != null
                        ? expense.getPaidBy().getFullName() : expense.getPaidBy().getUsername());
            }
            if (expense.getSplits() != null) row.put("sharedBy", expense.getSplits().size());
            if (expense.getActivityId() != null) row.put("activityId", expense.getActivityId());
            rows.add(row);
            if (rows.size() >= MAX_EXPENSES) break;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", all.size());
        out.put("expenses", rows);
        return out;
    }

    private Map<String, Object> tripDetails(AssistantTurn turn, Map<String, Object> args) {
        Trip trip = tripAccessGuard.requireAccess(requiredUuid(args.get("trip_id"), "trip_id"), turn.getUserId());
        Map<String, Object> out = new LinkedHashMap<>(tripSnapshots.snapshot(trip));
        out.put("canEdit", tripAccessGuard.canEdit(trip, turn.getUserId()));
        return out;
    }

    /**
     * Itineraries other travellers made public: around the asked city or trip day (the Feed's
     * ranking — many stops and photos, recent first — or by title words), or the ones that
     * include one place. The trip of this chat and the asker's own trips are left out.
     */
    private Map<String, Object> publicTrips(AssistantTurn turn, Trip trip, Map<String, Object> args) {
        int limit = (int) clamp(number(args.get("limit")), 5, 1, MAX_PUBLIC_TRIPS);
        UUID placeId = uuid(args.get("place_id"));
        String words = text(args.get("query"));
        Map<String, Object> out = new LinkedHashMap<>();
        List<PublicTripResponse> found;
        if (placeId != null) {
            Place place = places.findById(placeId).filter(this::isPublic)
                    .orElseThrow(() -> new IllegalArgumentException("Place not found"));
            out.put("withPlace", place.getTitle());
            found = tripService.getPublicTripsWithPlace(placeId, 0, limit + 1, turn.getUserId());
        } else {
            Anchor anchor = anchor(trip, args);
            out.put("near", anchor.label());
            found = tripService.searchPublicTrips(anchor.lat(), anchor.lng(), BigDecimal.valueOf(PUBLIC_TRIP_RADIUS_KM),
                    null, words, false, null, false, words == null, null, 0, limit + 1, turn.getUserId(), turn.getUserId());
        }
        UUID ownTripId = trip == null ? null : trip.getId();
        List<Map<String, Object>> rows = found.stream()
                .filter(t -> t.getId() != null && !t.getId().equals(ownTripId) && !Boolean.TRUE.equals(t.getIsOwnTrip()))
                .limit(limit)
                .map(this::publicTripRow)
                .toList();
        out.put("trips", rows);
        if (rows.isEmpty()) out.put("note", "No public itinerary found; do not invent one.");
        return out;
    }

    private Map<String, Object> publicTripRow(PublicTripResponse trip) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", trip.getId());
        row.put("name", trip.getName());
        row.put("kind", "trip");
        putIfPresent(row, "destination", truncate(trip.getDestination(), 60));
        if (trip.getStartDate() != null && trip.getEndDate() != null) {
            row.put("days", ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1);
        }
        putIfPresent(row, "by", trip.getOwnerName());
        putIfPresent(row, "stops", trip.getTotalActivities());
        putIfPresent(row, "photos", trip.getTotalMemoryCount());
        putIfPresent(row, "views", trip.getViewCount());
        putIfPresent(row, "copies", trip.getCopyCount());
        putIfPresent(row, "route", truncate(trip.getRouteSummary(), 120));
        putIfPresent(row, "desc", truncate(trip.getDescription(), 120));
        return row;
    }

    /**
     * What app travellers did and said at one place: how many trips plan it and how many
     * check-ins it has, the latest public check-ins, members' reviews, and public trips that
     * include it. Only public content; private check-ins never reach the model.
     */
    private Map<String, Object> placeCommunity(AssistantTurn turn, Map<String, Object> args) {
        Place place = places.findById(requiredUuid(args.get("place_id"), "place_id"))
                .filter(this::isPublic)
                .orElseThrow(() -> new IllegalArgumentException("Place not found"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", place.getId());
        out.put("name", place.getTitle());
        out.put("kind", kindOf(place));
        putUsage(out, place);
        out.put("recentCheckins", checkinService.byPlace(null, place.getId(), 0, MAX_COMMUNITY_ROWS).stream()
                .map(this::checkinRow)
                .toList());
        List<Map<String, Object>> reviews = new ArrayList<>();
        for (UserReviewResponse review : reviewService.getPlaceReviews(place.getId(), turn.getUserId(), 0, MAX_COMMUNITY_ROWS)) {
            Map<String, Object> row = new LinkedHashMap<>();
            putIfPresent(row, "by", review.getUserName());
            putIfPresent(row, "rating", review.getOverallRating());
            putIfPresent(row, "text", truncate(review.getText(), 160));
            if (review.getCreatedAt() != null) row.put("date", review.getCreatedAt().toLocalDate());
            reviews.add(row);
        }
        out.put("reviews", reviews);
        out.put("publicTrips", tripService.getPublicTripsWithPlace(place.getId(), 0, 3, turn.getUserId()).stream()
                .map(this::publicTripRow)
                .toList());
        out.put("note", "Counts, check-ins and reviews come from Pengoo users, not Google.");
        return out;
    }

    /**
     * Check-ins members made during the trip, grouped by where they were: "where did we check
     * in most". Only public check-ins are linked into a trip's record, so private ones are absent.
     */
    private Map<String, Object> tripCheckins(AssistantTurn turn, Trip trip, Map<String, Object> args) {
        Trip target = tripFor(turn, trip, args);
        List<UserCheckinResponse> checkins = checkinService.byTrip(turn.getUserId(), target.getId());
        Map<String, Map<String, Object>> byPlace = new LinkedHashMap<>();
        Map<String, Set<String>> people = new LinkedHashMap<>();
        for (UserCheckinResponse checkin : checkins) {
            String name = checkinPlaceName(checkin);
            String key = checkin.getPlaceId() != null ? checkin.getPlaceId().toString() : String.valueOf(name);
            Map<String, Object> row = byPlace.computeIfAbsent(key, ignored -> {
                Map<String, Object> place = new LinkedHashMap<>();
                putIfPresent(place, "placeId", checkin.getPlaceId());
                place.put("name", name);
                place.put("checkins", 0);
                return place;
            });
            row.put("checkins", (Integer) row.get("checkins") + 1);
            if (checkin.getUserDisplayName() != null) {
                people.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(checkin.getUserDisplayName());
            }
        }
        byPlace.forEach((key, row) -> row.put("people", List.copyOf(people.getOrDefault(key, Set.of()))));
        List<Map<String, Object>> ranked = byPlace.values().stream()
                .sorted(Comparator.comparingInt((Map<String, Object> row) -> (Integer) row.get("checkins")).reversed())
                .limit(10)
                .toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("trip", target.getName());
        out.put("total", checkins.size());
        out.put("byPlace", ranked);
        out.put("latest", checkins.stream()
                .sorted(Comparator.comparing(UserCheckinResponse::getVisitedAt,
                        Comparator.nullsFirst(Comparator.<java.time.LocalDateTime>naturalOrder())).reversed())
                .limit(MAX_TRIP_CHECKINS)
                .map(this::checkinRow)
                .toList());
        out.put("note", checkins.isEmpty()
                ? "No public check-in is linked to this trip yet; private ones are not visible."
                : "Public check-ins linked to this trip only; private ones are not visible.");
        return out;
    }

    /**
     * The trip's own catalogue places ranked by how much app travellers use them (trips that
     * plan them, check-ins there): "which of our stops are the popular ones".
     */
    private Map<String, Object> rankTripPlaces(AssistantTurn turn, Trip trip, Map<String, Object> args) {
        Trip target = tripFor(turn, trip, args);
        Map<UUID, List<Integer>> days = new LinkedHashMap<>();
        for (Activity activity : activities.findByTripId(target.getId())) {
            UUID placeId = AssistantTripSnapshots.catalogueId(activity);
            if (placeId == null) continue;
            List<Integer> onDays = days.computeIfAbsent(placeId, ignored -> new ArrayList<>());
            if (activity.getDayNumber() != null && !onDays.contains(activity.getDayNumber())) {
                onDays.add(activity.getDayNumber());
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!days.isEmpty()) {
            for (Place place : places.findByIds(new ArrayList<>(days.keySet()))) {
                if (!isPublic(place)) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", place.getId());
                row.put("name", place.getTitle());
                row.put("kind", kindOf(place));
                row.put("days", days.get(place.getId()));
                row.put("trips", place.getTripUseCount() == null ? 0 : place.getTripUseCount());
                row.put("checkins", place.getCheckinCount() == null ? 0 : place.getCheckinCount());
                rows.add(row);
            }
        }
        rows.sort(Comparator.comparingInt((Map<String, Object> row) ->
                (Integer) row.get("trips") + (Integer) row.get("checkins")).reversed());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("trip", target.getName());
        out.put("places", rows.stream().limit(15).toList());
        out.put("note", "trips = distinct app trips that plan the place (this one included); checkins = app users' check-ins there.");
        return out;
    }

    /** The chat's trip, or in the 1:1 chat one the asker names and may read. */
    private Trip tripFor(AssistantTurn turn, Trip trip, Map<String, Object> args) {
        return trip != null ? trip
                : tripAccessGuard.requireAccess(requiredUuid(args.get("trip_id"), "trip_id"), turn.getUserId());
    }

    private Map<String, Object> checkinRow(UserCheckinResponse checkin) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", checkin.getId());
        row.put("name", checkinPlaceName(checkin));
        row.put("kind", "checkin");
        putIfPresent(row, "placeId", checkin.getPlaceId());
        putIfPresent(row, "by", checkin.getUserDisplayName());
        if (checkin.getVisitedAt() != null) row.put("date", checkin.getVisitedAt().toLocalDate());
        putIfPresent(row, "rating", checkin.getOverallRating());
        putIfPresent(row, "caption", truncate(checkin.getCaption(), 120));
        if (checkin.getPhotos() != null && !checkin.getPhotos().isEmpty()) row.put("photos", checkin.getPhotos().size());
        return row;
    }

    private static String checkinPlaceName(UserCheckinResponse checkin) {
        if (checkin.getPlaceName() != null && !checkin.getPlaceName().isBlank()) return checkin.getPlaceName();
        if (checkin.getCustomName() != null && !checkin.getCustomName().isBlank()) return checkin.getCustomName();
        return checkin.getLocationName() != null ? checkin.getLocationName() : checkin.getProvince();
    }

    /**
     * Every destination of the catalogue (tourist areas) for "where should we go", in catalogue
     * priority order, each with its slogan and the guide's own words on what it is, its vibe and
     * its season. Nothing is filtered or scored here: the guide is free text ("tháng 11-3",
     * "tránh mùa mưa bão tháng 7-9", "đẹp nhất mùa thu"), so the model reads it and picks.
     */
    private Map<String, Object> exploreDestinations() {
        List<LocationImage> all = new ArrayList<>(areas.findAll());
        all.sort(Comparator.comparing((LocationImage area) -> area.getPriority() == null ? 0 : area.getPriority()).reversed());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LocationImage area : all) {
            Map<String, String> guide = guideSections(area.getDescription());
            Map<String, Object> row = destinationRow(area);
            putIfPresent(row, "about", guide.get("DESCRIPTION"));
            putIfPresent(row, "vibe", guide.get("VIBE"));
            putIfPresent(row, "season", guide.get("SEASON"));
            rows.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("destinations", rows);
        out.put("note", rows.isEmpty()
                ? "The catalogue has no destination; answer from general knowledge without cards."
                : "All catalogue destinations with their guide's own words. get_destination_guide adds where to stay, "
                  + "food and notes. Show picks as destination cards.");
        return out;
    }

    /** One destination's guide as the admins wrote it: about, vibe, where to stay, food, season, notes. */
    private Map<String, Object> destinationGuide(Map<String, Object> args) {
        UUID id = uuid(args.get("destination_id"));
        LocationImage area;
        if (id != null) {
            area = areas.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown destination"));
        } else {
            String city = text(args.get("city"));
            if (city == null) throw new IllegalArgumentException("Give destination_id or city");
            area = areas.findBestMatch(normalize(city)).flatMap(found -> areas.findById(found.getId()))
                    .orElseThrow(() -> new IllegalArgumentException("Unknown destination: " + city));
        }
        Map<String, Object> out = destinationRow(area);
        Map<String, String> guide = guideSections(area.getDescription());
        GUIDE_KEYS.forEach((type, key) -> putIfPresent(out, key, truncate(guide.get(type), MAX_GUIDE_CHARS)));
        if (guide.isEmpty()) out.put("note", "This destination has no written guide yet; use your own knowledge and say so.");
        return out;
    }

    private Map<String, Object> destinationRow(LocationImage area) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", area.getId());
        row.put("name", area.getFullAddress());
        row.put("kind", "destination");
        putIfPresent(row, "slogan", truncate(area.getSlogan(), 80));
        return row;
    }

    /** The guide's non-empty sections by type (DESCRIPTION, VIBE, ACCOMODATION, CUISINE, SEASON, NOTES). */
    private Map<String, String> guideSections(String json) {
        Map<String, String> sections = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return sections;
        try {
            for (com.fasterxml.jackson.databind.JsonNode section : objectMapper.readTree(json)) {
                String type = section.path("type").asText(null);
                String content = section.path("content").path("content").asText(null);
                if (type != null && content != null && !content.isBlank()) sections.put(type, content.trim());
            }
        } catch (Exception e) {
            log.debug("Assistant: unreadable destination guide: {}", e.getMessage());
        }
        return sections;
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

    private Map<String, Object> placeRow(Place place, Anchor anchor, LocalDate hoursOn, boolean padding) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", place.getId());
        row.put("name", place.getTitle());
        boolean food = place.getPlaceGroup() == PlaceGroup.FOOD_AND_DRINK;
        row.put("kind", kindOf(place));
        if (padding) row.put("nameMatch", false);
        if (place.getSubTypes() != null && place.getSubTypes().length > 0) {
            row.put("types", List.of(place.getSubTypes()).subList(0, Math.min(3, place.getSubTypes().length)));
        }
        putIfPresent(row, "rating", place.getReviewRating());
        putIfPresent(row, "reviews", place.getReviewCount());
        putUsage(row, place);
        putIfPresent(row, "price", PlaceAiFacts.priceLabel(place.getPriceRange(), place.getAttributes()));
        putIfPresent(row, "tags", emptyToNull(PlaceAiFacts.aboutTags(place.getAbout())));
        putIfPresent(row, "hours", dayHours(place.getOpenHours(), hoursOn));
        if (place.getLatitude() != null && place.getLongitude() != null) {
            row.put("distanceKm", round(haversineKm(anchor.lat(), anchor.lng(), place.getLatitude(), place.getLongitude()), 1));
        }
        if (food) putIfPresent(row, "menu", emptyToNull(PlaceAiFacts.menuHighlights(place.getMenu(), 4)));
        putIfPresent(row, "desc", truncate(place.getAiDescription(), 140));
        return row;
    }

    /**
     * How many app travellers used the place: distinct trips that plan it and check-ins there.
     * This, not the Google rating, is what "popular" means in answers.
     */
    private static void putUsage(Map<String, Object> row, Place place) {
        putIfPresent(row, "trips", place.getTripUseCount());
        putIfPresent(row, "checkins", place.getCheckinCount());
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

    private static Map<String, String> guideKeys() {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("DESCRIPTION", "about");
        keys.put("VIBE", "vibe");
        keys.put("ACCOMODATION", "stay");
        keys.put("CUISINE", "food");
        keys.put("SEASON", "season");
        keys.put("NOTES", "notes");
        return keys;
    }

    private static PlaceSearchSort sortOf(String value) {
        if ("popular".equalsIgnoreCase(value)) return PlaceSearchSort.POPULARITY;
        return "rating".equalsIgnoreCase(value) ? PlaceSearchSort.RATING : null;
    }

    private static String kindOf(Place place) {
        if (place.getPlaceGroup() == PlaceGroup.FOOD_AND_DRINK) return "food";
        return place.getPlaceGroup() == PlaceGroup.ACCOMMODATION ? "stay" : "sight";
    }

    private boolean isPublic(Place place) {
        return place.getVisibilityStatus() == null || place.getVisibilityStatus() == PlaceVisibilityStatus.ACTIVE;
    }

    private static boolean inArea(Place place, LocationImage area) {
        return area == null || place.getLocationImageId() == null || area.getId().equals(place.getLocationImageId());
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

    private static LocalDate date(Object value) {
        String text = text(value);
        if (text == null) return null;
        try {
            return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Dates must be yyyy-MM-dd");
        }
    }

    private static <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private static Integer integer(Object value) {
        Double number = number(value);
        return number == null ? null : number.intValue();
    }

    /** A list argument; a single string is read as a one-item list. */
    private static List<String> strings(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(AssistantToolService::text).filter(Objects::nonNull).toList();
        }
        String single = text(value);
        return single == null ? null : List.of(single);
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
