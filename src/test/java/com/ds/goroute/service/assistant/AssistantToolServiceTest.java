package com.ds.goroute.service.assistant;

import com.ds.goroute.dto.response.PlaceResponse;
import com.ds.goroute.dto.response.PublicTripResponse;
import com.ds.goroute.dto.response.UserCheckinResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.AssistantTurn;
import com.ds.goroute.entity.LocationImage;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.localevent.service.LocalEventPublicService;
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
import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.PlaceSearchSort;
import com.ds.goroute.type.PlaceVisibilityStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssistantToolServiceTest {

    private static final UUID TURN = UUID.randomUUID();
    private static final UUID TRIP = UUID.randomUUID();
    private static final UUID ASKER = UUID.randomUUID();

    @Mock private AssistantTurnService turns;
    @Mock private TripRepository trips;
    @Mock private ActivityRepository activities;
    @Mock private PlaceRepository places;
    @Mock private PlaceService placeService;
    @Mock private LocationImageRepository areas;
    @Mock private CityWeatherService weatherService;
    @Mock private LocalEventPublicService eventService;
    @Mock private MeetupService meetupService;
    @Mock private FoodService foodService;
    @Mock private ExpenseService expenseService;
    @Mock private TripAccessGuard tripAccessGuard;
    @Mock private TripService tripService;
    @Mock private UserCheckinService checkinService;
    @Mock private ReviewService reviewService;
    @Mock private AssistantTripSnapshots tripSnapshots;

    private AssistantToolService service;

    @BeforeEach
    void setUp() {
        service = new AssistantToolService(turns, trips, activities, places, placeService, areas, weatherService,
                eventService, meetupService, foodService, expenseService, tripAccessGuard, tripService,
                checkinService, reviewService, tripSnapshots, new ObjectMapper());
        when(turns.requireRunning(TURN)).thenReturn(AssistantTurn.builder().id(TURN).tripId(TRIP).userId(ASKER).build());
        when(trips.findById(TRIP)).thenReturn(Optional.of(Trip.builder().id(TRIP).name("Đà Lạt")
                .destination("Đà Lạt").destinationLat(new BigDecimal("11.94")).destinationLng(new BigDecimal("108.44"))
                .build()));
        when(tripSnapshots.destinationsOf(any())).thenReturn(List.of());
    }

    private static Place place(UUID id, String name, int trips, int checkins) {
        return Place.builder().id(id).title(name).placeGroup(PlaceGroup.ATTRACTIONS)
                .visibilityStatus(PlaceVisibilityStatus.ACTIVE)
                .latitude(new BigDecimal("11.94")).longitude(new BigDecimal("108.44"))
                .tripUseCount(trips).checkinCount(checkins).build();
    }

    @Test
    void popularRanksByAppUsageAndShowsTheCounts() {
        UUID lake = UUID.randomUUID();
        when(placeService.searchPlaces(isNull(), any(), any(), any(), isNull(), any(), any(), isNull(),
                eq(PlaceSearchSort.POPULARITY), isNull(), isNull(), isNull(), eq(false), isNull(), eq(0), anyInt()))
                .thenReturn(List.of(PlaceResponse.builder().id(lake).build()));
        when(places.findByIds(anyList())).thenReturn(List.of(place(lake, "Hồ Xuân Hương", 120, 45)));

        Map<String, Object> out = service.execute(TURN, "search_places", Map.of("kind", "sight", "sort", "popular"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("places");
        assertThat(rows).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("trips", 120).containsEntry("checkins", 45));
        verify(places, never()).findActiveForAiInArea(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void theTripsStopsAreRankedByHowManyTravellersUseThem() {
        UUID quiet = UUID.randomUUID();
        UUID famous = UUID.randomUUID();
        when(activities.findByTripId(TRIP)).thenReturn(List.of(
                Activity.builder().tripId(TRIP).dayNumber(1).placeRefId(quiet).build(),
                Activity.builder().tripId(TRIP).dayNumber(2).placeRefId(famous).build(),
                Activity.builder().tripId(TRIP).dayNumber(3).placeRefId(famous).build(),
                Activity.builder().tripId(TRIP).dayNumber(3).build()));
        when(places.findByIds(anyList())).thenReturn(List.of(place(quiet, "Quán nhỏ", 1, 0), place(famous, "Chợ Đà Lạt", 80, 30)));

        Map<String, Object> out = service.execute(TURN, "rank_trip_places", Map.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("places");
        assertThat(rows).extracting(row -> row.get("name")).containsExactly("Chợ Đà Lạt", "Quán nhỏ");
        assertThat(rows.get(0)).containsEntry("days", List.of(2, 3));
    }

    @Test
    void theTripsCheckinsAreGroupedByPlaceMostFirst() {
        UUID market = UUID.randomUUID();
        when(checkinService.byTrip(ASKER, TRIP)).thenReturn(List.of(
                checkin(null, "Đồi chè", "An", 1),
                checkin(market, "Chợ đêm", "An", 2),
                checkin(market, "Chợ đêm", "Bình", 3)));

        Map<String, Object> out = service.execute(TURN, "get_trip_checkins", Map.of());

        assertThat(out).containsEntry("total", 3);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byPlace = (List<Map<String, Object>>) out.get("byPlace");
        assertThat(byPlace.get(0)).containsEntry("name", "Chợ đêm").containsEntry("checkins", 2)
                .containsEntry("people", List.of("An", "Bình"));
        assertThat(byPlace.get(1)).containsEntry("name", "Đồi chè").containsEntry("checkins", 1);
    }

    @Test
    void publicTripsLeaveOutThisTripAndTheAskersOwn() {
        UUID other = UUID.randomUUID();
        when(tripService.searchPublicTrips(any(), any(), any(), isNull(), isNull(), eq(false), isNull(), eq(false),
                eq(true), eq(0), anyInt(), eq(ASKER), eq(ASKER))).thenReturn(List.of(
                PublicTripResponse.builder().id(TRIP).name("Chuyến này").build(),
                PublicTripResponse.builder().id(UUID.randomUUID()).name("Của mình").isOwnTrip(true).build(),
                PublicTripResponse.builder().id(other).name("Đà Lạt 3 ngày").ownerName("Chi")
                        .startDate(LocalDate.of(2026, 9, 1)).endDate(LocalDate.of(2026, 9, 3)).totalActivities(12).build()));

        Map<String, Object> out = service.execute(TURN, "search_public_trips", Map.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("trips");
        assertThat(rows).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("id", other).containsEntry("kind", "trip").containsEntry("days", 3L)
                .containsEntry("stops", 12));
        verify(tripService, never()).getPublicTripsWithPlace(any(), anyInt(), anyInt(), any());
    }

    @Test
    void everyDestinationComesWithTheGuidesOwnWordsInPriorityOrder() {
        String guide = "[{\"type\":\"SEASON\",\"content\":{\"title\":\"Nên đi mùa nào\","
                + "\"content\":\"Đẹp nhất mùa hoa tam giác mạch (tháng 10-11), tránh mưa lũ tháng 7-8\"}},"
                + "{\"type\":\"VIBE\",\"content\":{\"title\":\"Vibe\",\"content\":\"Hùng vĩ\"}},"
                + "{\"type\":\"NOTES\",\"content\":{\"title\":\"Lưu ý\",\"content\":null}}]";
        LocationImage haGiang = area("Hà Giang", 5, guide);
        LocationImage daNang = area("Đà Nẵng", 10, null);
        LocationImage tokyo = area("Tokyo", 1, null);
        when(areas.findAll()).thenReturn(List.of(haGiang, tokyo, daNang));

        Map<String, Object> out = service.execute(TURN, "explore_destinations", Map.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("destinations");
        assertThat(rows).extracting(row -> row.get("name")).containsExactly("Đà Nẵng", "Hà Giang", "Tokyo");
        assertThat(rows.get(1)).containsEntry("kind", "destination").containsEntry("vibe", "Hùng vĩ")
                .containsEntry("season", "Đẹp nhất mùa hoa tam giác mạch (tháng 10-11), tránh mưa lũ tháng 7-8");

        when(areas.findById(haGiang.getId())).thenReturn(Optional.of(haGiang));
        Map<String, Object> full = service.execute(TURN, "get_destination_guide",
                Map.of("destination_id", haGiang.getId().toString()));
        assertThat(full).containsKeys("season", "vibe").doesNotContainKey("notes");
    }

    private static LocationImage area(String name, int priority, String guide) {
        return LocationImage.builder().id(UUID.randomUUID()).fullAddress(name).priority(priority)
                .description(guide).build();
    }

    private static UserCheckinResponse checkin(UUID placeId, String name, String by, int day) {
        return UserCheckinResponse.builder().id(UUID.randomUUID()).placeId(placeId).placeName(name)
                .userDisplayName(by).visitedAt(LocalDateTime.of(2026, 10, day, 10, 0)).build();
    }
}
