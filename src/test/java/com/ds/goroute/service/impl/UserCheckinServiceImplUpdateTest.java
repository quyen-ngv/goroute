package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.CreateUserCheckinRequest;
import com.ds.goroute.dto.request.UpdateUserCheckinRequest;
import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.entity.UserReview;
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
import com.ds.goroute.type.CheckinPhotoSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserCheckinServiceImplUpdateTest {

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

    /**
     * The order is the contract. The cleanup is asked what may go while the old photo rows
     * are still readable and once the review has been rewritten, so a photo the author took
     * off the visit is judged against what the visit and its review show afterwards -- not
     * against the list that still had it a moment ago.
     */
    @Test
    void removingAPhotoDeletesItsObjectAfterTheReviewIsRewrittenAndBeforeTheRowsGo() {
        UUID userId = UUID.randomUUID();
        UUID checkinId = UUID.randomUUID();
        UUID placeId = UUID.randomUUID();
        UserCheckin checkin = UserCheckin.builder()
                .id(checkinId)
                .userId(userId)
                .placeId(placeId)
                .overallRating(4)
                .build();
        when(checkins.findById(checkinId)).thenReturn(Optional.of(checkin));
        when(checkins.update(any(UserCheckin.class))).thenReturn(1);
        when(checkins.findPhotos(checkinId)).thenReturn(List.of());
        when(reviews.findByUserAndPlace(userId, placeId)).thenReturn(Optional.empty());
        when(reviews.findById(any(UUID.class))).thenReturn(Optional.empty());
        when(places.findById(placeId)).thenReturn(Optional.empty());
        when(users.findById(userId)).thenReturn(Optional.empty());

        UpdateUserCheckinRequest request = new UpdateUserCheckinRequest();
        request.setOverallRating(4);
        request.setPhotos(List.of(cameraPhoto("https://cdn/kept.webp")));

        service.update(userId, checkinId, request);

        InOrder ordered = inOrder(reviews, images, checkins);
        ordered.verify(reviews).save(any(UserReview.class));
        ordered.verify(images).deleteImagesForEntityRecord(
                "USER_CHECKIN_PHOTO", checkinId, List.of("https://cdn/kept.webp"));
        ordered.verify(checkins).deletePhotos(checkinId);
    }

    private static CreateUserCheckinRequest.CheckinPhotoInput cameraPhoto(String url) {
        CreateUserCheckinRequest.CheckinPhotoInput photo = new CreateUserCheckinRequest.CheckinPhotoInput();
        photo.setUrl(url);
        photo.setSource(CheckinPhotoSource.CAMERA);
        return photo;
    }
}
