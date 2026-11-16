package com.ds.goroute.localevent.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateActivityRequest;
import com.ds.goroute.dto.response.ActivityResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.localevent.domain.LocalEvent;
import com.ds.goroute.localevent.dto.LocalEventAddToTripRequest;
import com.ds.goroute.localevent.persistence.LocalEventRepository;
import com.ds.goroute.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Putting a local event into a trip (epic 17, V285): one itinerary stop made from the event, so
 * its name, venue and coordinates cannot drift from what the event says. Who may edit the trip
 * is {@link ActivityService}'s rule; this only decides what the stop says.
 */
@Service
@RequiredArgsConstructor
public class LocalEventTripService {

    /** The activity category used when the app sends none. */
    static final String DEFAULT_CATEGORY = "entertainment";

    private final LocalEventRepository events;
    private final ActivityService activityService;

    @Transactional
    public ActivityResponse addToTrip(UUID userId, UUID eventId, UUID tripId, LocalEventAddToTripRequest request) {
        LocalEvent event = events.findPublishedById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.LOCAL_EVENT_NOT_FOUND, "Event not found"));
        String address = Stream.of(event.getVenueName(), event.getAddress())
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(", "));
        CreateActivityRequest activity = CreateActivityRequest.builder()
                .name(event.getTitle())
                .address(address.isEmpty() ? null : address)
                .lat(event.getLatitude())
                .lng(event.getLongitude())
                .dayNumber(request.getDayNumber())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .category(request.getCategory() == null || request.getCategory().isBlank()
                        ? DEFAULT_CATEGORY : request.getCategory().trim())
                .localEventId(event.getId())
                .build();
        return activityService.createActivity(tripId, activity, userId);
    }
}
