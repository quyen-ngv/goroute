package com.ds.goroute.localevent.service;

import com.ds.goroute.utils.GeoBoundingBox;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.localevent.domain.LocalEvent;
import com.ds.goroute.localevent.dto.LocalEventDetailResponse;
import com.ds.goroute.localevent.dto.LocalEventSummaryResponse;
import com.ds.goroute.localevent.persistence.LocalEventGeoSearchParams;
import com.ds.goroute.localevent.persistence.LocalEventRepository;
import com.ds.goroute.type.LocalEventCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The app's read side (GET /v1/api/public/events and /{id}). Public data, no flag check: the app
 * decides whether to show the Home tab (EVENT/ENABLED or beta), the endpoints always answer.
 */
@Service
@RequiredArgsConstructor
public class LocalEventPublicService {

    /** Default and longest window of a search. */
    static final Duration DEFAULT_WINDOW = Duration.ofDays(60);
    static final Duration MAX_WINDOW = Duration.ofDays(366);

    private final LocalEventRepository repository;
    private final LocalEventResponses responses;

    Clock clock = Clock.systemUTC();

    @Transactional(readOnly = true)
    public PageResponse<LocalEventSummaryResponse> search(double latitude, double longitude, double radiusKm,
                                                         String from, String to, String category,
                                                         int page, int size) {
        Instant fromInstant = from == null || from.isBlank() ? clock.instant() : instant("from", from);
        Instant toInstant = to == null || to.isBlank() ? fromInstant.plus(DEFAULT_WINDOW) : instant("to", to);
        if (!toInstant.isAfter(fromInstant)) {
            throw invalid("to must be after from");
        }
        if (toInstant.isAfter(fromInstant.plus(MAX_WINDOW))) {
            throw invalid("to must be at most 366 days after from");
        }
        GeoBoundingBox box = GeoBoundingBox.around(latitude, longitude, radiusKm);
        LocalEventGeoSearchParams params = LocalEventGeoSearchParams.builder()
                .latitude(latitude)
                .longitude(longitude)
                .radiusKm(radiusKm)
                .minLat(box.minLat())
                .maxLat(box.maxLat())
                .minLng(box.minLng())
                .maxLng(box.maxLng())
                .from(LocalEventTimes.toUtc(fromInstant))
                .to(LocalEventTimes.toUtc(toInstant))
                .categories(categories(category))
                .limit(size)
                .offset(page * size)
                .build();
        List<LocalEventSummaryResponse> items = repository.findPublicWithinRadius(params).stream()
                .map(responses::summary)
                .toList();
        long total = repository.countPublicWithinRadius(params);
        return PageResponse.of(items, total, page, size);
    }

    /**
     * List cards of the events among {@code eventIds} the public list would show now (Feed
     * search, epic 12 §4.4); the others are absent. Any order.
     */
    @Transactional(readOnly = true)
    public List<LocalEventSummaryResponse> visibleSummaries(java.util.Collection<UUID> eventIds) {
        return repository.findPublicSummariesByIds(eventIds, LocalEventTimes.toUtc(clock.instant())).stream()
                .map(responses::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public LocalEventDetailResponse detail(UUID eventId) {
        LocalEvent event = repository.findPublishedById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.LOCAL_EVENT_NOT_FOUND, "Event not found"));
        return responses.publicDetail(event, repository.findImagesByEvent(eventId));
    }

    /** Comma list of category codes; unknown codes are a 400, not silently ignored. */
    static List<String> categories(String value) {
        List<String> codes = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return codes;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String part : value.split(",")) {
            String code = part.trim().toUpperCase(Locale.ROOT);
            if (code.isEmpty()) {
                continue;
            }
            try {
                LocalEventCategory.valueOf(code);
            } catch (IllegalArgumentException exception) {
                throw invalid("Unknown category " + code);
            }
            if (seen.add(code)) {
                codes.add(code);
            }
        }
        return codes;
    }

    private static Instant instant(String name, String value) {
        return LocalEventTimes.parseInstant(value)
                .orElseThrow(() -> invalid(name + " must be ISO-8601 with an offset or Z"));
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorConstant.INVALID_PARAMETERS, message);
    }
}
