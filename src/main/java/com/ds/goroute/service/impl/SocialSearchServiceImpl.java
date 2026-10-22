package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.SocialSearchHits;
import com.ds.goroute.dto.response.DiscoverUserResponse;
import com.ds.goroute.dto.response.PublicTripResponse;
import com.ds.goroute.dto.response.SocialSearchCountsResponse;
import com.ds.goroute.dto.response.SocialSearchItemResponse;
import com.ds.goroute.dto.response.SocialSearchResponse;
import com.ds.goroute.dto.response.UserCheckinResponse;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.SocialSearchIndexService;
import com.ds.goroute.service.SocialSearchService;
import com.ds.goroute.service.TripService;
import com.ds.goroute.service.UserBlockService;
import com.ds.goroute.service.UserCheckinService;
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
    /** People shown above the posts on the first page of "All". */
    static final int USER_PREVIEW_SIZE = 5;

    private final SocialSearchIndexService index;
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
                .items(List.of());
        if (searchableLength(query) < MIN_QUERY_LENGTH) {
            return response.counts(page == 0 ? SocialSearchCountsResponse.builder().build() : null).build();
        }
        try {
            long offset = (long) page * size;
            SocialSearchHits found = offset >= SocialSearchIndexService.MAX_RESULT_WINDOW
                    ? SocialSearchHits.EMPTY
                    : index.search(query, type.listedKinds(), viewerId, (int) offset, size);
            List<SocialSearchHits.Hit> preview = type == SocialSearchType.ALL && page == 0
                    ? index.search(query, Set.of(SocialSearchType.USER), viewerId, 0, USER_PREVIEW_SIZE).hits()
                    : List.of();
            if (page == 0) {
                Map<SocialSearchType, Integer> counts = index.countByKind(query, viewerId);
                response.counts(SocialSearchCountsResponse.builder()
                        .users(counts.getOrDefault(SocialSearchType.USER, 0))
                        .checkins(counts.getOrDefault(SocialSearchType.CHECKIN, 0))
                        .trips(counts.getOrDefault(SocialSearchType.TRIP, 0))
                        .build());
            }

            List<SocialSearchHits.Hit> all = new ArrayList<>(found.hits());
            all.addAll(preview);
            Cards cards = loadCards(viewerId, all);
            List<SocialSearchItemResponse> items = new ArrayList<>(found.hits().size());
            for (SocialSearchHits.Hit hit : found.hits()) {
                SocialSearchItemResponse item = cards.item(hit);
                if (item != null) {
                    items.add(item);
                }
            }
            List<DiscoverUserResponse> users = preview.stream()
                    .map(hit -> cards.users().get(hit.id()))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            boolean hasMore = offset + size < Math.min(found.totalHits(), SocialSearchIndexService.MAX_RESULT_WINDOW);
            return response.items(items).users(users).hasMore(hasMore).build();
        } catch (IOException e) {
            log.error("Social search failed for type {}", type, e);
            throw new BusinessException(ErrorConstant.INTERNAL_SERVER_ERROR, "Search failed");
        }
    }

    /**
     * The cards of every hit the viewer may see: one query per kind for the whole page, then
     * one for the authors' accounts and one for blocks. The index may lag behind a deletion, a
     * privacy change or a takedown; this is where those drop out.
     */
    private Cards loadCards(UUID viewerId, List<SocialSearchHits.Hit> hits) {
        Map<UUID, DiscoverUserResponse> users = userRepository.findDiscoverProfilesByIds(idsOf(hits, SocialSearchType.USER))
                .stream()
                .collect(Collectors.toMap(DiscoverUserResponse::getId, Function.identity(), (first, second) -> first));
        Map<UUID, UserCheckinResponse> checkins =
                new HashMap<>(checkinService.findVisibleByIds(viewerId, idsOf(hits, SocialSearchType.CHECKIN)));
        Map<UUID, PublicTripResponse> trips = tripService.getPublicTripCards(viewerId, idsOf(hits, SocialSearchType.TRIP))
                .stream()
                .collect(Collectors.toMap(PublicTripResponse::getId, Function.identity(), (first, second) -> first));

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
        return new Cards(users, checkins, trips);
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
                         Map<UUID, PublicTripResponse> trips) {

        SocialSearchItemResponse item(SocialSearchHits.Hit hit) {
            return switch (hit.type()) {
                case USER -> users.containsKey(hit.id())
                        ? SocialSearchItemResponse.builder().type(SocialSearchType.USER).user(users.get(hit.id())).build()
                        : null;
                case CHECKIN -> checkins.containsKey(hit.id())
                        ? SocialSearchItemResponse.builder()
                                .type(SocialSearchType.CHECKIN).checkin(checkins.get(hit.id())).build()
                        : null;
                case TRIP -> trips.containsKey(hit.id())
                        ? SocialSearchItemResponse.builder().type(SocialSearchType.TRIP).trip(trips.get(hit.id())).build()
                        : null;
                case ALL -> null;
            };
        }
    }
}
