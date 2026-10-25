package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.AssignActivityPlaceRequest;
import com.ds.goroute.dto.response.AdminUnlinkedActivityResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.UnlinkedActivityRow;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.TripRealtimePublisher;
import com.ds.goroute.service.redis.RedisService;
import com.ds.goroute.type.TripRealtimeEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityPlaceAdminServiceImplTest {

    @Mock private ActivityRepository activities;
    @Mock private TripRepository trips;
    @Mock private PlaceRepository places;
    @Mock private UserRepository users;
    @Mock private RedisService redis;
    @Mock private TripRealtimePublisher realtime;

    @InjectMocks private ActivityPlaceAdminServiceImpl service;

    private final UUID adminId = UUID.randomUUID();
    private final UUID tripId = UUID.randomUUID();
    private final UUID activityId = UUID.randomUUID();
    private final UUID placeId = UUID.randomUUID();

    @Test
    void assigningLinksTheActivityAndTellsOpenTripClients() {
        Activity activity = Activity.builder().id(activityId).tripId(tripId).placeId("ChIJlegacyGoogleId").build();
        Place place = Place.builder().id(placeId)
                .latitude(new BigDecimal("16.0544")).longitude(new BigDecimal("108.2022")).build();
        when(activities.findById(activityId)).thenReturn(Optional.of(activity));
        when(trips.findById(tripId)).thenReturn(Optional.of(Trip.builder().id(tripId).build()));
        when(places.findById(placeId)).thenReturn(Optional.of(place));
        when(activities.assignPlace(activityId, placeId, place.getLatitude(), place.getLongitude())).thenReturn(1);

        service.assignPlace(adminId, activityId, request(placeId));

        verify(activities).assignPlace(activityId, placeId, place.getLatitude(), place.getLongitude());
        verify(redis).delete("activities:" + tripId);
        verify(realtime).publishAfterCommit(TripRealtimeEventType.ACTIVITY_UPDATED, tripId, activityId, adminId);
    }

    @Test
    void anActivityAlreadyPointingAtACatalogueRowIsRefused() {
        UUID existingPlaceId = UUID.randomUUID();
        Activity activity = Activity.builder().id(activityId).tripId(tripId).placeId(existingPlaceId.toString()).build();
        when(activities.findById(activityId)).thenReturn(Optional.of(activity));
        when(trips.findById(tripId)).thenReturn(Optional.of(Trip.builder().id(tripId).build()));
        when(places.findByIds(List.of(existingPlaceId)))
                .thenReturn(List.of(Place.builder().id(existingPlaceId).build()));

        assertThatThrownBy(() -> service.assignPlace(adminId, activityId, request(placeId)))
                .isInstanceOf(BusinessException.class)
                .extracting("error.code").isEqualTo(ErrorConstant.ALREADY_PROCESSED);
        verify(activities, never()).assignPlace(any(), any(), any(), any());
    }

    @Test
    void anActivityOfADeletedTripIsNotFound() {
        when(activities.findById(activityId))
                .thenReturn(Optional.of(Activity.builder().id(activityId).tripId(tripId).build()));
        when(trips.findById(tripId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignPlace(adminId, activityId, request(placeId)))
                .isInstanceOf(BusinessException.class)
                .extracting("error.code").isEqualTo(ErrorConstant.NOT_FOUND);
        verify(activities, never()).assignPlace(any(), any(), any(), any());
    }

    @Test
    void anUnknownPlaceIsRejectedBeforeAnythingIsWritten() {
        when(activities.findById(activityId))
                .thenReturn(Optional.of(Activity.builder().id(activityId).tripId(tripId).build()));
        when(trips.findById(tripId)).thenReturn(Optional.of(Trip.builder().id(tripId).build()));
        when(places.findById(placeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignPlace(adminId, activityId, request(placeId)))
                .isInstanceOf(BusinessException.class)
                .extracting("error.code").isEqualTo(ErrorConstant.PLACE_NOT_FOUND);
        verify(activities, never()).assignPlace(any(), any(), any(), any());
    }

    @Test
    void listingCarriesTheTripOwnerAndTrimsTheSearch() {
        UUID ownerId = UUID.randomUUID();
        UnlinkedActivityRow row = UnlinkedActivityRow.builder()
                .id(activityId).tripId(tripId).tripName("Đà Nẵng 3N2Đ").ownerId(ownerId)
                .name("Bún chả cá bà Lữ").address("319 Hùng Vương, Đà Nẵng").build();
        when(activities.findUnlinkedForAdmin("bún", false, 50, 50)).thenReturn(List.of(row));
        when(users.findByIds(List.of(ownerId)))
                .thenReturn(List.of(User.builder().id(ownerId).fullName("Lan").email("lan@example.com").build()));

        List<AdminUnlinkedActivityResponse> result = service.listUnlinked("  bún ", false, 1, 50);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.getTripName()).isEqualTo("Đà Nẵng 3N2Đ");
            assertThat(item.getOwnerDisplayName()).isEqualTo("Lan");
            assertThat(item.getOwnerEmail()).isEqualTo("lan@example.com");
            assertThat(item.getAddress()).isEqualTo("319 Hùng Vương, Đà Nẵng");
        });
    }

    @Test
    void dismissedListingNamesTheOperatorWhoDismissed() {
        UUID ownerId = UUID.randomUUID();
        UnlinkedActivityRow row = UnlinkedActivityRow.builder()
                .id(activityId).tripId(tripId).ownerId(ownerId).dismissedBy(adminId).build();
        when(activities.findUnlinkedForAdmin(null, true, 50, 0)).thenReturn(List.of(row));
        when(users.findByIds(List.of(ownerId, adminId))).thenReturn(List.of(
                User.builder().id(ownerId).fullName("Lan").build(),
                User.builder().id(adminId).fullName("Ops").build()));

        List<AdminUnlinkedActivityResponse> result = service.listUnlinked(" ", true, 0, 50);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.getOwnerDisplayName()).isEqualTo("Lan");
            assertThat(item.getDismissedByName()).isEqualTo("Ops");
        });
    }

    @Test
    void dismissingTakesTheActivityOutOfTheQueue() {
        when(activities.findById(activityId))
                .thenReturn(Optional.of(Activity.builder().id(activityId).tripId(tripId).build()));
        when(activities.dismissPlaceReview(activityId, adminId)).thenReturn(1);

        service.dismiss(adminId, activityId);

        verify(activities).dismissPlaceReview(activityId, adminId);
    }

    @Test
    void dismissingAnAlreadyDismissedActivityIsAConflict() {
        when(activities.findById(activityId))
                .thenReturn(Optional.of(Activity.builder().id(activityId).tripId(tripId).build()));
        when(activities.dismissPlaceReview(activityId, adminId)).thenReturn(0);

        assertThatThrownBy(() -> service.dismiss(adminId, activityId))
                .isInstanceOf(BusinessException.class)
                .extracting("error.code").isEqualTo(ErrorConstant.ALREADY_PROCESSED);
    }

    @Test
    void restoringAMissingActivityIsNotFound() {
        when(activities.findById(activityId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restore(adminId, activityId))
                .isInstanceOf(BusinessException.class)
                .extracting("error.code").isEqualTo(ErrorConstant.NOT_FOUND);
        verify(activities, never()).restorePlaceReview(any());
    }

    private AssignActivityPlaceRequest request(UUID target) {
        AssignActivityPlaceRequest request = new AssignActivityPlaceRequest();
        request.setPlaceId(target);
        return request;
    }
}
