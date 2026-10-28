package com.ds.goroute.service.assistant;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.AssistantReplyRequest;
import com.ds.goroute.dto.request.CreateTripRequest;
import com.ds.goroute.dto.request.TripDestinationRequest;
import com.ds.goroute.dto.request.UpdateTripRequest;
import com.ds.goroute.dto.response.AssistantTripPlanResponse;
import com.ds.goroute.dto.response.TripResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.AssistantProposal;
import com.ds.goroute.entity.LocationImage;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.LocationImageRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.TextModerationService;
import com.ds.goroute.service.TripRealtimePublisher;
import com.ds.goroute.service.TripService;
import com.ds.goroute.type.ActivityStatus;
import com.ds.goroute.type.AssistantProposalAction;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.PlaceVisibilityStatus;
import com.ds.goroute.type.TransportMode;
import com.ds.goroute.type.TripRealtimeEventType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The trip plans Pengoo writes in chat: a whole new trip (NEW_TRIP) or the days of an existing
 * one (PLAN_DAYS). Both carry the same day-by-day plan; NEW_TRIP adds the trip's own fields.
 *
 * <p>Building checks every value of the worker's draft: catalogue places must be public (their
 * name, address and coordinates are taken from the catalogue), free text goes through the
 * moderation filter (applying writes through repositories and services, so the controllers'
 * {@code @ModeratedText} never runs), times and money must parse, days must fit the trip. A bad
 * value is dropped, a stop without a usable name is dropped, and a plan left with no stop is not
 * proposed at all. The cleaned plan is stored in the payload as {@code plan}; the card reads it
 * back as {@link AssistantTripPlanResponse}.
 *
 * <p>Applying writes all stops in one go (one insert each, one {@code activity.reordered} event),
 * not one notification per stop as adding them by hand would.
 */
@Component
@RequiredArgsConstructor
class AssistantTripPlans {

    static final int MAX_ITEMS_PER_DAY = 14;
    static final int MAX_ITEMS = 120;
    static final int MAX_DAY_TITLE = 120;
    private static final int HISTORY_PLAN_CHARS = 2500;
    private static final ZoneId LOCAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Pattern CATEGORY = Pattern.compile("[a-z_]{2,40}");

    private final PlaceRepository places;
    private final LocationImageRepository areas;
    private final ActivityRepository activities;
    private final TripService tripService;
    private final AssistantTripDays tripDays;
    private final TripRealtimePublisher realtime;
    private final TextModerationService moderation;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------ build

    /**
     * Fills the payload of a NEW_TRIP; false when the draft has no destination or name, no usable
     * stop, or a length the app does not allow. A start date in the past is dropped (the user picks
     * dates when applying) rather than the plan.
     */
    boolean buildNewTrip(AssistantReplyRequest.TripPlanDraft draft, Map<String, Object> payload) {
        if (draft == null) return false;
        AssistantTripPlanResponse.AssistantTripPlanResponseBuilder plan = AssistantTripPlanResponse.builder();
        LocationImage area = area(draft.getDestination());
        String destination = area != null ? area.getFullAddress()
                : checkedText(draft.getDestination(), 120, ModeratedContentType.TRIP, ModerationVisibility.PUBLIC);
        String name = checkedText(draft.getName(), AssistantTripEdits.MAX_NAME, ModeratedContentType.TRIP,
                ModerationVisibility.PUBLIC);
        if (name == null) name = destination;
        if (name == null) return false;

        int listedDays = draft.getDays() == null ? 0 : draft.getDays().stream()
                .map(AssistantReplyRequest.PlanDay::getDayNumber)
                .filter(day -> day != null && day >= 1)
                .max(Integer::compare).orElse(0);
        int dayCount = Math.max(draft.getDayCount() == null ? 0 : draft.getDayCount(), listedDays);
        if (dayCount < 1 || dayCount > AssistantActionService.MAX_TRIP_DAYS) return false;

        List<AssistantTripPlanResponse.Day> days = days(draft.getDays(), dayCount);
        int items = itemCount(days);
        if (items == 0) return false;

        LocalDate start = date(draft.getStartDate());
        if (start != null && start.isBefore(LocalDate.now(LOCAL_ZONE))) start = null;
        String currency = AssistantTripEdits.currency(draft.getCurrency());
        BigDecimal budget = AssistantTripEdits.amount(draft.getBudget(), false);
        LocalTime departure = time(draft.getDepartureTime());

        plan.name(name)
                .destination(destination)
                .cityId(area == null ? null : area.getId().toString())
                .description(checkedText(draft.getDescription(), AssistantTripEdits.MAX_TEXT, ModeratedContentType.TRIP,
                        ModerationVisibility.PUBLIC))
                .startDate(start == null ? null : start.toString())
                .endDate(start == null ? null : start.plusDays(dayCount - 1L).toString())
                .dayCount(dayCount)
                .budget(budget == null ? null : budget.toPlainString())
                .currency(currency == null ? "VND" : currency)
                .startingPointName(checkedText(draft.getStartingPointName(), AssistantTripEdits.MAX_NAME,
                        ModeratedContentType.TRIP, ModerationVisibility.GROUP))
                .startingPointAddress(checkedText(draft.getStartingPointAddress(), AssistantTripEdits.MAX_ADDRESS,
                        ModeratedContentType.TRIP, ModerationVisibility.GROUP))
                .departureTime(departure == null ? null : departure.toString())
                .itemCount(items)
                .days(days);
        payload.put("plan", toMap(plan.build()));
        if (area != null && area.getLatitude() != null && area.getLongitude() != null) {
            payload.put("destinationLat", area.getLatitude().toPlainString());
            payload.put("destinationLng", area.getLongitude().toPlainString());
        }
        payload.put("activityCount", items);
        payload.put("dayNumber", 1);
        payload.put("toDayNumber", dayCount);
        if (start != null) {
            payload.put("newStartDate", start.toString());
            payload.put("newEndDate", start.plusDays(dayCount - 1L).toString());
        }
        return true;
    }

    /**
     * Fills the payload of a PLAN_DAYS for {@code trip}; false without a usable stop. Days past the
     * trip's end lengthen it when applied, up to the app's longest trip.
     */
    boolean buildPlanDays(Trip trip, AssistantReplyRequest.TripPlanDraft draft, Map<String, Object> payload) {
        if (draft == null || trip.getStartDate() == null || trip.getEndDate() == null) return false;
        int tripDays = AssistantTripSnapshots.dayCount(trip);
        List<AssistantTripPlanResponse.Day> days = days(draft.getDays(), AssistantActionService.MAX_TRIP_DAYS);
        int items = itemCount(days);
        if (items == 0) return false;
        int firstDay = days.get(0).getDayNumber();
        int lastDay = days.get(days.size() - 1).getDayNumber();
        payload.put("plan", toMap(AssistantTripPlanResponse.builder()
                .name(trip.getName())
                .destination(trip.getDestination())
                .startDate(trip.getStartDate().toString())
                .endDate(trip.getStartDate().plusDays(Math.max(tripDays, lastDay) - 1L).toString())
                .dayCount(Math.max(tripDays, lastDay))
                .itemCount(items)
                .days(days)
                .build()));
        payload.put("activityCount", items);
        payload.put("dayNumber", firstDay);
        payload.put("toDayNumber", lastDay);
        if (lastDay > tripDays) {
            payload.put("newStartDate", trip.getStartDate().toString());
            payload.put("newEndDate", trip.getStartDate().plusDays(lastDay - 1L).toString());
        }
        return true;
    }

    /** The plan's days in order, each day once, inside 1..{@code maxDay}, with only the stops that check out. */
    private List<AssistantTripPlanResponse.Day> days(List<AssistantReplyRequest.PlanDay> drafts, int maxDay) {
        if (drafts == null || drafts.isEmpty()) return List.of();
        Map<UUID, Place> catalogue = catalogue(drafts);
        TreeMap<Integer, AssistantTripPlanResponse.Day> byNumber = new TreeMap<>();
        int total = 0;
        for (AssistantReplyRequest.PlanDay draft : drafts) {
            Integer number = draft == null ? null : draft.getDayNumber();
            if (number == null || number < 1 || number > maxDay) continue;
            AssistantTripPlanResponse.Day day = byNumber.computeIfAbsent(number, n -> AssistantTripPlanResponse.Day.builder()
                    .dayNumber(n).build());
            if (day.getTitle() == null) {
                day.setTitle(checkedText(draft.getTitle(), MAX_DAY_TITLE, ModeratedContentType.TRIP,
                        ModerationVisibility.GROUP));
            }
            for (AssistantReplyRequest.PlanItem raw : draft.getItems() == null
                    ? List.<AssistantReplyRequest.PlanItem>of() : draft.getItems()) {
                if (day.getItems().size() >= MAX_ITEMS_PER_DAY || total >= MAX_ITEMS) break;
                AssistantTripPlanResponse.Item item = item(raw, number, maxDay, catalogue);
                if (item == null) continue;
                day.getItems().add(item);
                total++;
            }
        }
        byNumber.values().removeIf(day -> day.getItems().isEmpty());
        return new ArrayList<>(byNumber.values());
    }

    private Map<UUID, Place> catalogue(List<AssistantReplyRequest.PlanDay> drafts) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (AssistantReplyRequest.PlanDay day : drafts) {
            if (day == null || day.getItems() == null) continue;
            for (AssistantReplyRequest.PlanItem item : day.getItems()) {
                UUID id = item == null ? null : AssistantProposalService.uuid(item.getPlaceId());
                if (id != null) ids.add(id);
            }
        }
        return publicPlaces(ids);
    }

    private Map<UUID, Place> publicPlaces(Set<UUID> ids) {
        Map<UUID, Place> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        for (Place place : places.findByIds(new ArrayList<>(ids))) {
            if (place.getVisibilityStatus() == null || place.getVisibilityStatus() == PlaceVisibilityStatus.ACTIVE) {
                out.put(place.getId(), place);
            }
        }
        return out;
    }

    /** One stop; null when it has no usable name (and no catalogue place to take one from). */
    private AssistantTripPlanResponse.Item item(AssistantReplyRequest.PlanItem raw, int dayNumber, int maxDay,
                                                Map<UUID, Place> catalogue) {
        if (raw == null) return null;
        Place place = catalogue.get(AssistantProposalService.uuid(raw.getPlaceId()));
        String name = place != null ? truncate(place.getTitle(), AssistantTripEdits.MAX_NAME)
                : checkedText(raw.getName(), AssistantTripEdits.MAX_NAME, ModeratedContentType.ACTIVITY,
                ModerationVisibility.GROUP);
        if (name == null || name.isBlank()) return null;
        LocalTime start = time(raw.getStartTime());
        LocalTime end = time(raw.getEndTime());
        if (end != null && (start == null || !end.isAfter(start))) end = null;
        TransportMode mode = transportMode(raw.getTransportMode());
        boolean stay = Boolean.TRUE.equals(raw.getStay())
                || (place != null && place.getPlaceGroup() == PlaceGroup.ACCOMMODATION);
        Integer untilDay = stay && raw.getUntilDay() != null && raw.getUntilDay() > dayNumber && raw.getUntilDay() <= maxDay
                ? raw.getUntilDay() : null;
        BigDecimal cost = AssistantTripEdits.amount(raw.getCost(), true);
        String costCurrency = cost == null ? null : AssistantTripEdits.currency(raw.getCostCurrency());
        // A stop the map found (no catalogue place): where it is, so the activity lands on the map.
        String mapProvider = AssistantMapPlace.provider(raw.getMapProvider());
        String mapPlaceId = AssistantMapPlace.trimmed(raw.getMapPlaceId());
        boolean onMap = place == null && AssistantMapPlace.hasLocation(mapProvider, mapPlaceId, raw.getLat(), raw.getLng());
        return AssistantTripPlanResponse.Item.builder()
                .placeId(place == null ? null : place.getId().toString())
                .name(name)
                .startTime(start == null ? null : start.toString())
                .endTime(end == null ? null : end.toString())
                .category(category(raw.getCategory(), place, mode, stay))
                .notes(checkedText(raw.getNotes(), AssistantTripEdits.MAX_TEXT, ModeratedContentType.ACTIVITY,
                        ModerationVisibility.GROUP))
                .description(checkedText(raw.getDescription(), AssistantTripEdits.MAX_TEXT, ModeratedContentType.ACTIVITY,
                        ModerationVisibility.GROUP))
                .address(place != null ? truncate(place.getAddress(), AssistantTripEdits.MAX_ADDRESS)
                        : checkedText(raw.getAddress(), AssistantTripEdits.MAX_ADDRESS, ModeratedContentType.ACTIVITY,
                        ModerationVisibility.GROUP))
                .endAddress(mode == null ? null : checkedText(raw.getEndAddress(), AssistantTripEdits.MAX_ADDRESS,
                        ModeratedContentType.ACTIVITY, ModerationVisibility.GROUP))
                .transportMode(mode == null ? null : mode.name())
                .cost(cost == null ? null : cost.toPlainString())
                .costCurrency(costCurrency)
                .stay(stay ? Boolean.TRUE : null)
                .untilDay(untilDay)
                .lat(onMap ? raw.getLat() : null)
                .lng(onMap ? raw.getLng() : null)
                .mapProvider(onMap ? mapProvider : null)
                .mapPlaceId(onMap ? mapPlaceId : null)
                .build();
    }

    /** The app's activity categories are lower-case words; the place, the leg or the stay gives the default. */
    private static String category(String requested, Place place, TransportMode mode, boolean stay) {
        String value = requested == null ? null : requested.trim().toLowerCase(Locale.ROOT);
        if (value != null && CATEGORY.matcher(value).matches()) return value;
        if (mode != null) return "transport";
        if (stay) return "hotel";
        if (place != null && place.getPlaceGroup() == PlaceGroup.FOOD_AND_DRINK) return "restaurant";
        return "attraction";
    }

    // ------------------------------------------------------------------ apply

    /**
     * Creates the planned trip as {@code viewerId}, who becomes its owner, then writes its stops.
     * The dates are the ones picked when applying, else the plan's own; a plan without dates
     * cannot be applied until some are picked.
     *
     * @return the new trip
     */
    UUID createTrip(Map<String, Object> payload, LocalDate pickedStart, UUID viewerId) {
        AssistantTripPlanResponse plan = view(payload.get("plan"));
        if (plan == null || plan.getDayCount() == null) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "The plan is unreadable");
        }
        LocalDate start = pickedStart != null ? pickedStart : date(plan.getStartDate());
        if (start == null) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Choose the trip's start date");
        }
        if (start.isBefore(LocalDate.now(LOCAL_ZONE))) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "The start date has already passed");
        }
        LocalDate end = start.plusDays(plan.getDayCount() - 1L);
        BigDecimal lat = decimal(payload.get("destinationLat"));
        BigDecimal lng = decimal(payload.get("destinationLng"));
        String destination = plan.getDestination();
        List<TripDestinationRequest> destinations = destination == null || lat == null || lng == null ? null
                : List.of(TripDestinationRequest.builder()
                .name(destination).address(destination).lat(lat).lng(lng)
                .orderIndex(0).startDate(start).endDate(end).isPrimary(true)
                .build());
        TripResponse trip = tripService.createTrip(CreateTripRequest.builder()
                .name(plan.getName())
                .startDate(start)
                .endDate(end)
                .destination(destination)
                .destinationLat(lat)
                .destinationLng(lng)
                .destinations(destinations)
                .budget(decimal(plan.getBudget()))
                .currency(plan.getCurrency())
                .build(), viewerId);
        LocalTime departure = time(plan.getDepartureTime());
        if (plan.getDescription() != null || plan.getStartingPointName() != null
                || plan.getStartingPointAddress() != null || departure != null) {
            tripService.updateTrip(trip.getId(), UpdateTripRequest.builder()
                    .description(plan.getDescription())
                    .startingPointName(plan.getStartingPointName())
                    .startingPointAddress(plan.getStartingPointAddress())
                    .startingPointTime(departure == null ? null : start.atTime(departure))
                    .build(), viewerId);
        }
        writeStops(trip.getId(), plan, viewerId);
        return trip.getId();
    }

    /** Writes a PLAN_DAYS into its trip, lengthening the trip first when the plan runs past its end. */
    void planDays(UUID tripId, Map<String, Object> payload, UUID viewerId) {
        AssistantTripPlanResponse plan = view(payload.get("plan"));
        if (plan == null || plan.getDays().isEmpty()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "The plan is unreadable");
        }
        int lastDay = plan.getDays().stream().mapToInt(AssistantTripPlanResponse.Day::getDayNumber).max().orElse(1);
        tripDays.extendTo(tripId, lastDay, viewerId);
        writeStops(tripId, plan, viewerId);
    }

    /**
     * One activity per stop, after the stops each day already has. Catalogue places are read again:
     * one that is no longer public stays in the plan by name only.
     */
    private void writeStops(UUID tripId, AssistantTripPlanResponse plan, UUID viewerId) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (AssistantTripPlanResponse.Day day : plan.getDays()) {
            for (AssistantTripPlanResponse.Item item : day.getItems()) {
                UUID id = AssistantProposalService.uuid(item.getPlaceId());
                if (id != null) ids.add(id);
            }
        }
        Map<UUID, Place> catalogue = publicPlaces(ids);
        Map<Integer, Integer> nextOrder = new HashMap<>();
        for (Activity existing : activities.findByTripId(tripId)) {
            if (existing.getDayNumber() == null) continue;
            int order = existing.getSortOrder() == null ? 0 : existing.getSortOrder() + 1;
            nextOrder.merge(existing.getDayNumber(), order, Math::max);
        }
        for (AssistantTripPlanResponse.Day day : plan.getDays()) {
            int order = nextOrder.getOrDefault(day.getDayNumber(), 0);
            for (AssistantTripPlanResponse.Item item : day.getItems()) {
                Place place = catalogue.get(AssistantProposalService.uuid(item.getPlaceId()));
                activities.insert(Activity.builder()
                        .id(UUID.randomUUID())
                        .tripId(tripId)
                        .dayNumber(day.getDayNumber())
                        .sortOrder(order++)
                        .placeRefId(place == null ? null : place.getId())
                        .placeId(place == null ? null : place.getPlaceId())
                        .name(item.getName())
                        .address(place != null && place.getAddress() != null
                                ? truncate(place.getAddress(), AssistantTripEdits.MAX_ADDRESS) : item.getAddress())
                        .lat(place == null ? item.getLat() : place.getLatitude())
                        .lng(place == null ? item.getLng() : place.getLongitude())
                        .mapboxId(place == null && AssistantMapPlace.MAPBOX.equals(item.getMapProvider())
                                ? item.getMapPlaceId() : null)
                        .endAddress(item.getEndAddress())
                        .startTime(time(item.getStartTime()))
                        .endTime(time(item.getEndTime()))
                        .endDayNumber(item.getUntilDay())
                        .estimatedCost(decimal(item.getCost()))
                        .costCurrency(item.getCostCurrency())
                        .category(item.getCategory())
                        .transportMode(transportMode(item.getTransportMode()))
                        .notes(item.getNotes())
                        .description(item.getDescription())
                        .status(ActivityStatus.CONFIRMED)
                        .isAccommodation(Boolean.TRUE.equals(item.getStay()))
                        .isStartingPoint(false)
                        .addedBy(viewerId)
                        .build());
            }
        }
        realtime.publishAfterCommit(TripRealtimeEventType.ACTIVITY_REORDERED, tripId, tripId, viewerId);
    }

    // ------------------------------------------------------------------ read

    /** The stored plan for the card; null when the payload holds none. */
    AssistantTripPlanResponse view(Object raw) {
        if (!(raw instanceof Map<?, ?>)) return null;
        try {
            return objectMapper.convertValue(raw, AssistantTripPlanResponse.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * What an earlier plan card says, for the history Pengoo reads with the next question: a
     * pending plan in full (so "swap day 2's café" can be answered with a revised plan), a
     * settled one in one line (an applied NEW_TRIP names the trip it created).
     */
    String historyNote(AssistantProposal proposal, Map<String, Object> payload) {
        AssistantTripPlanResponse plan = view(payload.get("plan"));
        if (plan == null) return null;
        String status = proposal.getStatus() == null ? "PENDING" : proposal.getStatus();
        boolean newTrip = AssistantProposalAction.NEW_TRIP.name().equals(proposal.getAction());
        if (!"PENDING".equals(status)) {
            if (newTrip && "APPLIED".equals(status) && proposal.getTripId() != null) {
                return "[trip plan \"" + plan.getName() + "\" applied: trip " + proposal.getTripId()
                        + " created; change it with proposals carrying that tripId]";
            }
            return "[trip plan \"" + plan.getName() + "\" " + status.toLowerCase(Locale.ROOT) + "]";
        }
        StringBuilder out = new StringBuilder("[trip plan card, not applied yet (")
                .append(proposal.getAction()).append("): \"").append(plan.getName()).append('"');
        if (plan.getDestination() != null) out.append(" · ").append(plan.getDestination());
        out.append(" · ").append(plan.getDayCount()).append(" days");
        out.append(plan.getStartDate() == null ? " · dates not picked" : " · from " + plan.getStartDate());
        for (AssistantTripPlanResponse.Day day : plan.getDays()) {
            out.append(" | D").append(day.getDayNumber()).append(':');
            for (AssistantTripPlanResponse.Item item : day.getItems()) {
                out.append(' ');
                if (item.getStartTime() != null) out.append(item.getStartTime()).append(' ');
                out.append(item.getName());
                if (item.getPlaceId() != null) out.append(" (").append(item.getPlaceId()).append(')');
                out.append(';');
            }
        }
        String text = out.append(']').toString();
        return text.length() > HISTORY_PLAN_CHARS ? text.substring(0, HISTORY_PLAN_CHARS) + "…]" : text;
    }

    // ------------------------------------------------------------------ helpers

    private static int itemCount(List<AssistantTripPlanResponse.Day> days) {
        return days.stream().mapToInt(day -> day.getItems().size()).sum();
    }

    private LocationImage area(String destination) {
        if (destination == null || destination.isBlank()) return null;
        return areas.findBestMatch(AssistantToolService.normalize(destination.trim()))
                .flatMap(found -> areas.findById(found.getId()))
                .orElse(null);
    }

    private Map<String, Object> toMap(AssistantTripPlanResponse plan) {
        return objectMapper.convertValue(plan, new TypeReference<Map<String, Object>>() {
        });
    }

    private String checkedText(String raw, int max, ModeratedContentType type, ModerationVisibility visibility) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty()) return null;
        if (value.length() > max) value = value.substring(0, max);
        return moderation.evaluate(type, "assistant.proposal", visibility, value, AssistantIdentity.BOT_USER_ID).blocks()
                ? null : value;
    }

    private static TransportMode transportMode(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return TransportMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        String text = value.trim();
        return text.length() > max ? text.substring(0, max - 1) + "…" : text;
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalTime time(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalTime.parse(value.trim().length() == 4 ? "0" + value.trim() : value.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static LocalDate date(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
