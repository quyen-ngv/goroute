package com.ds.goroute.service.assistant;

import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripDestination;
import com.ds.goroute.entity.User;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.MarketplaceChatService;
import com.ds.goroute.type.BusinessConfigKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pengoo's greeting in a new trip chat: a fixed line and four question chips, no model call and
 * nothing counted. Posted only when the trip's owner may use Pengoo, so nobody is greeted by an
 * assistant they cannot ask.
 */
@Service
@RequiredArgsConstructor
public class AssistantWelcomeService {

    static final List<String> TRIP_SUGGESTION_KEYS = List.of("DAY_FOOD", "WEATHER", "ROUTE", "NEARBY_SIGHTS");

    private final TripRepository trips;
    private final UserRepository users;
    private final AssistantAccessService access;
    private final AssistantTripSnapshots tripSnapshots;
    private final BusinessConfigService businessConfig;
    private final MarketplaceChatService chatService;

    @Transactional
    public void greetTrip(UUID conversationId, UUID tripId) {
        if (!businessConfig.getBoolean(BusinessConfigKey.ASSISTANT_WELCOME_ENABLED)) return;
        Trip trip = trips.findById(tripId).orElse(null);
        if (trip == null || !access.isAvailable(trip.getOwnerId())) return;
        String language = users.findById(trip.getOwnerId()).map(User::getLanguage).orElse("vi");
        String destination = destinationName(trip);
        int days = AssistantTripSnapshots.dayCount(trip);
        Map<String, Object> welcome = new LinkedHashMap<>();
        welcome.put("kind", "WELCOME");
        welcome.put("tripName", trip.getName());
        welcome.put("destination", destination);
        welcome.put("days", days);
        chatService.postAssistantMessage(conversationId,
                AssistantNotices.tripWelcome(language, trip.getName(), destination, days),
                List.of(welcome, Map.of("kind", "SUGGESTIONS", "keys", TRIP_SUGGESTION_KEYS)),
                null, null, "welcome:" + tripId);
    }

    private String destinationName(Trip trip) {
        List<TripDestination> destinations = tripSnapshots.destinationsOf(trip);
        if (!destinations.isEmpty() && destinations.get(0).getName() != null) return destinations.get(0).getName();
        return trip.getDestination();
    }
}
