package com.ds.goroute.service.marketplace;

import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.localevent.persistence.LocalEventRepository;
import com.ds.goroute.meetup.dto.MeetupDetailResponse;
import com.ds.goroute.meetup.service.MeetupService;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.FoodService;
import com.ds.goroute.type.ContentVisibility;
import com.ds.goroute.type.MeetupStatus;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.TripVisibility;
import com.ds.goroute.entity.Place;
import com.ds.goroute.dto.response.FoodDetailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Constructor;
import java.util.Arrays;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ChatShareValidator")
class ChatShareValidatorTest {

    private static final UUID SENDER = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();

    @Mock private PlaceRepository places;
    @Mock private FoodService foods;
    @Mock private UserCheckinRepository checkins;
    @Mock private ContentModerationService moderation;
    @Mock private TripRepository trips;
    @Mock private LocalEventRepository events;
    @Mock private MeetupService meetups;
    @Mock private ObjectProvider<FoodService> foodProvider;
    @Mock private ObjectProvider<MeetupService> meetupProvider;

    private ChatShareValidator validator;

    @BeforeEach
    void setUp() {
        when(foodProvider.getObject()).thenReturn(foods);
        when(meetupProvider.getObject()).thenReturn(meetups);
        validator = new ChatShareValidator(places, foodProvider, checkins, moderation, trips, events, meetupProvider);
    }

    @Test
    @DisplayName("does not take the meetup or food service at construction, which closed a bean cycle through chat")
    void hasNoConstructionCycleThroughMeetups() {
        Constructor<?> constructor = ChatShareValidator.class.getConstructors()[0];

        assertThat(Arrays.asList(constructor.getParameterTypes()))
                .doesNotContain(MeetupService.class, FoodService.class);
    }

    @Test
    @DisplayName("keeps only the reference of a place that exists")
    void acceptsAPlace() {
        when(places.findById(TARGET)).thenReturn(Optional.of(mock(Place.class)));

        List<Map<String, Object>> stored = validator.validate(SENDER, share("PLACE", null));

        assertThat(stored).containsExactly(Map.of("shareType", "PLACE", "targetId", TARGET.toString()));
    }

    @Test
    @DisplayName("needs the city of a dish, since a dish is public per city")
    void dishNeedsItsCity() {
        when(foods.getDetail(TARGET, "hanoi")).thenReturn(FoodDetailResponse.builder().build());

        assertThat(validator.validate(SENDER, share("FOOD", "hanoi")).get(0)).containsEntry("citySlug", "hanoi");
        assertThatThrownBy(() -> validator.validate(SENDER, share("FOOD", null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validator.validate(SENDER, share("PLACE", "hanoi"))).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("refuses a trip that is not public, even to its owner")
    void refusesAPrivateTrip() {
        Trip trip = new Trip();
        trip.setVisibility(TripVisibility.SHARED);
        when(trips.findById(TARGET)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> validator.validate(SENDER, share("TRIP", null))).isInstanceOf(BusinessException.class);

        trip.setVisibility(TripVisibility.PUBLIC);
        assertThat(validator.validate(SENDER, share("TRIP", null))).hasSize(1);
    }

    @Test
    @DisplayName("refuses a check-in that is private or taken down")
    void refusesAHiddenCheckin() {
        UserCheckin checkin = new UserCheckin();
        checkin.setId(TARGET);
        checkin.setVisibility(ContentVisibility.PUBLIC);
        when(checkins.findById(TARGET)).thenReturn(Optional.of(checkin));
        when(moderation.isTakenDown(ModeratedContentType.CHECKIN, TARGET)).thenReturn(true);

        assertThatThrownBy(() -> validator.validate(SENDER, share("CHECKIN", null))).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("refuses a meetup still waiting for review")
    void refusesAPendingMeetup() {
        MeetupDetailResponse meetup = new MeetupDetailResponse();
        meetup.setStatus(MeetupStatus.PENDING_REVIEW);
        when(meetups.detail(SENDER, TARGET)).thenReturn(meetup);

        assertThatThrownBy(() -> validator.validate(SENDER, share("MEETUP", null))).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("refuses an unknown kind, a bad id, extra fields, or more than one item")
    void refusesMalformedShares() {
        Map<String, Object> extra = new HashMap<>(share("PLACE", null).get(0));
        extra.put("url", "https://evil.example/x.png");
        for (List<Map<String, Object>> attachments : List.of(
                List.of(Map.<String, Object>of("shareType", "USER", "targetId", TARGET.toString())),
                List.of(Map.<String, Object>of("shareType", "PLACE", "targetId", "not-a-uuid")),
                List.of(extra),
                List.of(share("PLACE", null).get(0), share("PLACE", null).get(0)))) {
            assertThatThrownBy(() -> validator.validate(SENDER, attachments))
                    .as(attachments.toString())
                    .isInstanceOf(BusinessException.class);
        }
    }

    private List<Map<String, Object>> share(String type, String citySlug) {
        Map<String, Object> share = new HashMap<>();
        share.put("shareType", type);
        share.put("targetId", TARGET.toString());
        if (citySlug != null) share.put("citySlug", citySlug);
        return List.of(share);
    }
}
