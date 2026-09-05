package com.ds.goroute.service.impl;

import com.ds.goroute.dto.response.ActivityResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.CheckinRepository;
import com.ds.goroute.repository.ExpenseRepository;
import com.ds.goroute.repository.ExpenseSplitRepository;
import com.ds.goroute.repository.MediaAssetRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.service.redis.RedisService;
import com.ds.goroute.type.ActivityStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class ActivityServiceImplTest {

    @Mock private ActivityRepository activityRepository;
    @Mock private TripRepository tripRepository;
    @Mock private TripMemberRepository tripMemberRepository;
    @Mock private CheckinRepository checkinRepository;
    @Mock private RedisService redisService;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private UserRepository userRepository;
    @Mock private ExpenseSplitRepository expenseSplitRepository;
    @Mock private MediaAssetRepository mediaAssetRepository;
    @Mock private PlaceRepository placeRepository;
    @Mock private NotificationHelper notificationHelper;
    @Mock private ImageStorageCleanupService imageStorageCleanupService;
    @Mock private TripAccessGuard tripAccessGuard;

    @InjectMocks private ActivityServiceImpl service;

    @Test
    void getActivitiesBatchEnrichesPlaceThumbnailAddressAndSummary() {
        UUID tripId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        UUID placeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Activity activity = Activity.builder()
                .id(activityId)
                .tripId(tripId)
                .dayNumber(1)
                .placeRefId(placeId)
                .name("Museum visit")
                .address("Old address")
                .photoUrl("https://example.com/old.jpg")
                .status(ActivityStatus.CONFIRMED)
                .isAccommodation(false)
                .isStartingPoint(false)
                .build();
        Place place = Place.builder()
                .id(placeId)
                .placeId("google-place-id")
                .title("Museum")
                .address("Current place address")
                .latitude(new BigDecimal("16.0544"))
                .longitude(new BigDecimal("108.2022"))
                .reviewRating(new BigDecimal("4.8"))
                .thumbnail("https://example.com/place.jpg")
                .build();

        // Access control now lives in TripAccessGuard, which has its own test.
        when(tripAccessGuard.requireAccess(tripId, userId))
                .thenReturn(Trip.builder().id(tripId).ownerId(userId).build());
        when(activityRepository.findByTripId(tripId)).thenReturn(List.of(activity));
        when(placeRepository.findByIds(List.of(placeId))).thenReturn(List.of(place));
        when(placeRepository.findByPlaceIds(List.of())).thenReturn(List.of());
        when(checkinRepository.findByActivityId(activityId)).thenReturn(List.of());
        when(expenseRepository.findByActivityId(activityId)).thenReturn(List.of());
        when(mediaAssetRepository.findByEntityIds("EXPENSE", List.of())).thenReturn(List.of());

        List<ActivityResponse> result = service.getActivities(tripId, null, userId);

        assertThat(result).hasSize(1);
        ActivityResponse response = result.getFirst();
        assertThat(response.getAddress()).isEqualTo("Current place address");
        assertThat(response.getPhotoUrl()).isEqualTo("https://example.com/place.jpg");
        assertThat(response.getLat()).isEqualByComparingTo("16.0544");
        assertThat(response.getLng()).isEqualByComparingTo("108.2022");
        assertThat(response.getRating()).isEqualByComparingTo("4.8");
        assertThat(response.getPlace()).isNotNull();
        assertThat(response.getPlace().getId()).isEqualTo(placeId);
        assertThat(response.getPlace().getAddress()).isEqualTo("Current place address");
        assertThat(response.getPlace().getThumbnail()).isEqualTo("https://example.com/place.jpg");
        verify(placeRepository).findByIds(List.of(placeId));
        verify(placeRepository, never()).findById(any());
    }
}
