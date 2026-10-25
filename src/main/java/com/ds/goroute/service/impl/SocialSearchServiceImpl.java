package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.SocialSearchHits;
import com.ds.goroute.dto.response.ActivityBookingResponse;
import com.ds.goroute.dto.response.DiscoverUserResponse;
import com.ds.goroute.dto.response.MarketplaceActivityResponse;
import com.ds.goroute.dto.response.PublicTripResponse;
import com.ds.goroute.dto.response.SocialSearchCountsResponse;
import com.ds.goroute.dto.response.SocialSearchItemResponse;
import com.ds.goroute.dto.response.SocialSearchResponse;
import com.ds.goroute.dto.response.UserCheckinResponse;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.localevent.dto.LocalEventSummaryResponse;
import com.ds.goroute.localevent.service.LocalEventPublicService;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.ActivityBookingService;
import com.ds.goroute.service.ActivityCommerceService;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.PlaceSearchIndexService;
import com.ds.goroute.service.PlaceService;
import com.ds.goroute.service.SocialSearchIndexService;
import com.ds.goroute.service.SocialSearchService;
import com.ds.goroute.service.TripService;
import com.ds.goroute.service.UserBlockService;
import com.ds.goroute.service.UserCheckinService;
import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.SocialSearchType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialSearchServiceImpl implements SocialSearchService {

    static final int MIN_QUERY_LENGTH = 2;
    static final int MAX_QUERY_LENGTH = 100;
    /** People shown above the rows on the first page of "All". */
    static final int USER_PREVIEW_SIZE = 3;
    /** Places, stays, tours and events in each section of the first page of "All". */
    static final int ROW_PREVIEW_SIZE = 3;

    /** {@link SocialSearchType#PLACE}: where to eat, drink and look around. */
    static final List<String> PLACE_GROUPS = List.of(PlaceGroup.FOOD_AND_DRINK.name(), PlaceGroup.ATTRACTIONS.name());
    /** {@link SocialSearchType#STAY}: where to sleep. */
    static final List<String> STAY_GROUPS = List.of(PlaceGroup.ACCOMMODATION.name());

    private final SocialSearchIndexService index;
    private final PlaceSearchIndexService placeIndex;
    private final PlaceService placeService;
    private final ActivityCommerceService activityCommerceService;
    private final ActivityBookingService activityBookingService;
    private final LocalEventPublicService localEventService;
    private final BetaAccessService betaAccess;
    private final UserRepository userRepository;
    private final UserCheckinService checkinService;
    private final TripService tripService;
    private final UserBlockService blockService;

    @Override
    @Transactional(readOnly = true)
    public SocialSearchResponse search(UUID viewerId, String rawQuery, SocialSearchType type, int page, int size) {
        String query = normalize(rawQuery);
        SocialSearchResponse.SocialSearchResponseBuilder response = SocialSearchResponse.builder()
                .query(query)
                .type(type)
                .page(page)
                .size(size)
                .hasMore(false)
                .users(List.of())
                .places(List.of())
                .stays(List.of())
                .tours(List.of())
                .events(List.of())
                .items(List.of());
        if (searchableLength(query) < MIN_QUERY_LENGTH) {
            return response.counts(page == 0 ? SocialSearchCountsResponse.builder().build() : null).build();
        }
        // Marketplace beta users get partner tours, everyone else the imported catalogue, as on
        // Home (the app routes non-beta users away from marketplace screens).
        boolean marketplace = viewerId != null && betaAccess.isBetaUser(viewerId);
        String tourVariant = marketplace
                ? SocialSearchIndexService.TOUR_MARKETPLACE
                : SocialSearchIndexService.TOUR_CATALOGUE;
        try {
            long offset = (long) page * size;
            boolean firstPageOfAll = type == SocialSearchType.ALL && page == 0;
            List<SocialSearchHits.Hit> indexHits = new ArrayList<>();

            List<SocialSearchItemResponse> placeItems = null;
            SocialSearchHits found = SocialSearchHits.EMPTY;
            long total;
            if (type.isCataloguePlace()) {
                placeItems = offset >= SocialSearchIndexService.MAX_RESULT_WINDOW
                        ? List.of()
                        : placeItems(type, query, page, size);
                total = placeIndex.countPlacesByName(query, groupsOf(type));
            } else {
                if (offset < SocialSearchIndexService.MAX_RESULT_WINDOW) {
                    found = index.search(query, type.listedKinds(), viewerId, tourVariant, (int) offset, size);
                }
                indexHits.addAll(found.hits());
                total = found.totalHits();
            }

            List<SocialSearchHits.Hit> userPreview = List.of();
            List<SocialSearchHits.Hit> tourPreview = List.of();
            List<SocialSearchHits.Hit> eventPreview = List.of();
            if (firstPageOfAll) {
                userPreview = index.search(query, Set.of(SocialSearchType.USER), viewerId, null, 0, USER_PREVIEW_SIZE)
                        .hits();
                tourPreview = index.search(query, Set.of(SocialSearchType.TOUR), viewerId, tourVariant, 0,
                        ROW_PREVIEW_SIZE).hits();
                eventPreview = index.search(query, Set.of(SocialSearchType.EVENT), viewerId, null, 0,
                        ROW_PREVIEW_SIZE).hits();
                indexHits.addAll(userPreview);
                indexHits.addAll(tourPreview);
                indexHits.addAll(eventPreview);
                response.places(placeItems(SocialSearchType.PLACE, query, 0, ROW_PREVIEW_SIZE))
                        .stays(placeItems(SocialSearchType.STAY, query, 0, ROW_PREVIEW_SIZE));
            }
            if (page == 0) {
                response.counts(counts(query, viewerId, tourVariant));
            }

            Cards cards = loadCards(viewerId, indexHits, marketplace);
            response.items(placeItems != null ? placeItems : cards.items(found.hits()))
                    .users(userPreview.stream()
                            .map(hit -> cards.users().get(hit.id()))
                            .filter(java.util.Objects::nonNull)
                            .toList())
                    .tours(cards.items(tourPreview))
                    .events(cards.items(eventPreview));
            boolean hasMore = offset + size < Math.min(total, SocialSearchIndexService.MAX_RESULT_WINDOW);
            return response.hasMore(hasMore).build();
        } catch (IOException e) {
            log.error("Social search failed for type {}", type, e);
            throw new BusinessException(ErrorConstant.INTERNAL_SERVER_ERROR, "Search failed");
        }
    }

    private SocialSearchCountsResponse counts(String query, UUID viewerId, String tourVariant) throws IOException {
        Map<SocialSearchType, Integer> counts = index.countByKind(query, viewerId, tourVariant);
        return SocialSearchCountsResponse.builder()
                .users(counts.getOrDefault(SocialSearchType.USER, 0))
                .checkins(counts.getOrDefault(SocialSearchType.CHECKIN, 0))
                .trips(counts.getOrDefault(SocialSearchType.TRIP, 0))
                .tours(counts.getOrDefault(SocialSearchType.TOUR, 0))
                .events(counts.getOrDefault(SocialSearchType.EVENT, 0))
                .places(placeIndex.countPlacesByName(query, PLACE_GROUPS))
                .stays(placeIndex.countPlacesByName(query, STAY_GROUPS))
                .build();
    }

    private static List<String> groupsOf(SocialSearchType type) {
        return type == SocialSearchType.STAY ? STAY_GROUPS : PLACE_GROUPS;
    }

    /**
     * A page of catalogue places of {@code type}, best name match first, public only — the
     * {@code GET /places} name search.
     */
    private List<SocialSearchItemResponse> placeItems(SocialSearchType type, String query, int page, int size) {
        return placeService.getAllPlaces(query, groupsOf(type), false, page, size).stream()
                .map(place -> SocialSearchItemResponse.builder().type(type).place(place).build())
                .toList();
    }

    /**
     * The cards of every index hit the viewer may see: one query per kind for the whole page,
     * then one for the authors' accounts and one for blocks. The index may lag behind a
     * deletion, a privacy change, a takedown or an event ending; this is where those drop out.
     */
    private Cards loadCards(UUID viewerId, List<SocialSearchHits.Hit> hits, boolean marketplace) {
        Map<UUID, DiscoverUserResponse> users = byId(
                userRepository.findDiscoverProfilesByIds(idsOf(hits, SocialSearchType.USER)),
                DiscoverUserResponse::getId);
        Map<UUID, UserCheckinResponse> checkins =
                new HashMap<>(checkinService.findVisibleByIds(viewerId, idsOf(hits, SocialSearchType.CHECKIN)));
        Map<UUID, PublicTripResponse> trips = byId(
                tripService.getPublicTripCards(viewerId, idsOf(hits, SocialSearchType.TRIP)), PublicTripResponse::getId);
        List<UUID> tourIds = idsOf(hits, SocialSearchType.TOUR);
        Map<UUID, MarketplaceActivityResponse> activities = marketplace && !tourIds.isEmpty()
                ? byId(activityCommerceService.listPublicByIds(tourIds), MarketplaceActivityResponse::getId)
                : Map.of();
        Map<UUID, ActivityBookingResponse> activityBookings = !marketplace && !tourIds.isEmpty()
                ? byId(activityBookingService.listCatalogueByIds(tourIds), ActivityBookingResponse::getId)
                : Map.of();
        List<UUID> eventIds = idsOf(hits, SocialSearchType.EVENT);
        Map<UUID, LocalEventSummaryResponse> events = eventIds.isEmpty()
                ? Map.of()
                : byId(localEventService.visibleSummaries(eventIds), LocalEventSummaryResponse::getId);

        Set<UUID> authors = new HashSet<>();
        checkins.values().forEach(checkin -> authors.add(checkin.getUserId()));
        trips.values().forEach(trip -> authors.add(trip.getOwnerId()));
        authors.remove(null);
        Set<UUID> liveAuthors = authors.isEmpty() ? Set.of() : userRepository.findByIds(authors).stream()
                .map(User::getId)
                .collect(Collectors.toSet());
        Set<UUID> people = new HashSet<>(authors);
        people.addAll(users.keySet());
        Set<UUID> blocked = viewerId == null || people.isEmpty() ? Set.of() : blockService.blockedAmong(viewerId, people);

        users.keySet().removeAll(blocked);
        checkins.values().removeIf(checkin ->
                !liveAuthors.contains(checkin.getUserId()) || blocked.contains(checkin.getUserId()));
        trips.values().removeIf(trip -> !liveAuthors.contains(trip.getOwnerId()) || blocked.contains(trip.getOwnerId()));
        return new Cards(users, checkins, trips, activities, activityBookings, events);
    }

    private static <T> Map<UUID, T> byId(List<T> cards, Function<T, UUID> idOf) {
        return cards.stream().collect(Collectors.toMap(idOf, Function.identity(), (first, second) -> first,
                HashMap::new));
    }

    private static List<UUID> idsOf(Collection<SocialSearchHits.Hit> hits, SocialSearchType kind) {
        return hits.stream().filter(hit -> hit.type() == kind).map(SocialSearchHits.Hit::id).distinct().toList();
    }

    private static String normalize(String query) {
        String trimmed = query == null ? "" : query.strip();
        return trimmed.length() > MAX_QUERY_LENGTH ? trimmed.substring(0, MAX_QUERY_LENGTH) : trimmed;
    }

    /** "@a" is one character to search for, not two. */
    private static int searchableLength(String query) {
        return (query.startsWith("@") ? query.substring(1) : query).strip().length();
    }

    private record Cards(Map<UUID, DiscoverUserResponse> users,
                         Map<UUID, UserCheckinResponse> checkins,
                         Map<UUID, PublicTripResponse> trips,
                         Map<UUID, MarketplaceActivityResponse> activities,
                         Map<UUID, ActivityBookingResponse> activityBookings,
                         Map<UUID, LocalEventSummaryResponse> events) {

        /** The visible ones among {@code hits}, in their order. */
        List<SocialSearchItemResponse> items(List<SocialSearchHits.Hit> hits) {
            List<SocialSearchItemResponse> items = new ArrayList<>(hits.size());
            for (SocialSearchHits.Hit hit : hits) {
                SocialSearchItemResponse item = item(hit);
                if (item != null) {
                    items.add(item);
                }
            }
            return items;
        }

        private SocialSearchItemResponse item(SocialSearchHits.Hit hit) {
            SocialSearchItemResponse.SocialSearchItemResponseBuilder item =
                    SocialSearchItemResponse.builder().type(hit.type());
            return switch (hit.type()) {
                case USER -> users.containsKey(hit.id()) ? item.user(users.get(hit.id())).build() : null;
                case CHECKIN -> checkins.containsKey(hit.id()) ? item.checkin(checkins.get(hit.id())).build() : null;
                case TRIP -> trips.containsKey(hit.id()) ? item.trip(trips.get(hit.id())).build() : null;
                case TOUR -> activities.containsKey(hit.id())
                        ? item.activity(activities.get(hit.id())).build()
                        : activityBookings.containsKey(hit.id())
                        ? item.activityBooking(activityBookings.get(hit.id())).build()
                        : null;
                case EVENT -> events.containsKey(hit.id()) ? item.event(events.get(hit.id())).build() : null;
                case ALL, PLACE, STAY -> null;
            };
        }
    }
}
