package com.ds.goroute.service.impl;

import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.AiTripRepository;
import com.ds.goroute.repository.CheckinRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.repository.UserReviewRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.ReviewScoringService;
import com.ds.goroute.service.ReviewService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.checkin.CheckinRewardService;
import com.ds.goroute.service.checkin.LocationKeyFactory;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.service.notification.SocialNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCheckinServiceImplDeleteTest {

    @Mock private UserCheckinRepository checkins;
    @Mock private UserReviewRepository reviews;
    @Mock private PlaceRepository places;
    @Mock private UserRepository users;
    @Mock private AiTripRepository subscriptions;
    @Mock private ReviewScoringService scoring;
    @Mock private ReviewService reviewService;
    @Mock private ContentModerationService moderation;
    @Mock private ImageStorageCleanupService images;
    @Mock private BusinessConfigService config;
    @Mock private LocationKeyFactory locationKeyFactory;
    @Mock private org.springframework.context.ApplicationEventPublisher events;
    @Mock private SocialNotificationService socialNotifications;
    @Mock private TripAccessGuard tripAccessGuard;
    @Mock private CheckinRewardService rewards;
    @Mock private ActivityRepository activities;
    @Mock private CheckinRepository activityVisits;
    @Mock private NotificationHelper notifications;

    @InjectMocks private UserCheckinServiceImpl service;

    @Test
    void softDeletingACheckinPurgesItsPhotoRowsAfterSchedulingStorageCleanup() {
        UUID userId = UUID.randomUUID();
        UUID checkinId = UUID.randomUUID();
        UserCheckin checkin = UserCheckin.builder().id(checkinId).userId(userId).build();
        when(checkins.findById(checkinId)).thenReturn(Optional.of(checkin));
        when(checkins.markRemoved(checkinId, userId)).thenReturn(1);

        service.delete(userId, checkinId, false);

        var ordered = inOrder(images, checkins);
        ordered.verify(images).deleteImagesForEntityRecord("USER_CHECKIN_PHOTO", checkinId);
        ordered.verify(checkins).markRemoved(checkinId, userId);
        ordered.verify(checkins).deletePhotos(checkinId);
        verify(events).publishEvent(any(Object.class));
    }
}
