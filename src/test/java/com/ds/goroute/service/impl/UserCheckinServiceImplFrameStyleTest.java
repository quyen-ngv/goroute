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
import com.ds.goroute.dto.response.UserCheckinResponse;
import com.ds.goroute.type.CheckinFrameStyle;
import com.ds.goroute.type.CheckinPhotoSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserCheckinServiceImplFrameStyleTest {

    @Mock private UserCheckinRepository checkins;
    @Mock private com.ds.goroute.mapper.SavedPostMapper savedPosts;
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

    @Mock private com.ds.goroute.repository.ContentCommentRepository contentComments;

    @InjectMocks private UserCheckinServiceImpl service;

    /** The frame the author picks is stored with the visit and comes back on it. */
    @Test
    void anEditStoresTheChosenFrameAndReturnsIt() {
        UUID userId = UUID.randomUUID();
        UUID checkinId = UUID.randomUUID();
        stubOwnedCheckin(userId, checkinId, null);

        UpdateUserCheckinRequest request = request(CheckinFrameStyle.FILM_STRIP);

        UserCheckinResponse response = service.update(userId, checkinId, request);

        assertThat(savedFrame()).isEqualTo(CheckinFrameStyle.FILM_STRIP);
        assertThat(response.getFrameStyle()).isEqualTo(CheckinFrameStyle.FILM_STRIP);
    }

    /** The edit saves the visit whole: an edit sent without a frame takes it off. */
    @Test
    void anEditWithoutAFrameClearsTheOneStored() {
        UUID userId = UUID.randomUUID();
        UUID checkinId = UUID.randomUUID();
        stubOwnedCheckin(userId, checkinId, CheckinFrameStyle.STAMP);

        UserCheckinResponse response = service.update(userId, checkinId, request(null));

        assertThat(savedFrame()).isNull();
        assertThat(response.getFrameStyle()).isNull();
    }

    private void stubOwnedCheckin(UUID userId, UUID checkinId, CheckinFrameStyle frame) {
        UserCheckin checkin = UserCheckin.builder()
                .id(checkinId)
                .userId(userId)
                .frameStyle(frame)
                .build();
        when(checkins.findById(checkinId)).thenReturn(Optional.of(checkin));
        when(checkins.update(any(UserCheckin.class))).thenReturn(1);
        when(checkins.findPhotos(checkinId)).thenReturn(List.of());
        when(reviews.findById(any(UUID.class))).thenReturn(Optional.empty());
        when(users.findById(userId)).thenReturn(Optional.empty());
    }

    private CheckinFrameStyle savedFrame() {
        ArgumentCaptor<UserCheckin> saved = ArgumentCaptor.forClass(UserCheckin.class);
        verify(checkins).update(saved.capture());
        return saved.getValue().getFrameStyle();
    }

    private static UpdateUserCheckinRequest request(CheckinFrameStyle frame) {
        UpdateUserCheckinRequest request = new UpdateUserCheckinRequest();
        request.setFrameStyle(frame);
        request.setPhotos(List.of(cameraPhoto("https://cdn/photo.webp")));
        return request;
    }

    private static CreateUserCheckinRequest.CheckinPhotoInput cameraPhoto(String url) {
        CreateUserCheckinRequest.CheckinPhotoInput photo = new CreateUserCheckinRequest.CheckinPhotoInput();
        photo.setUrl(url);
        photo.setSource(CheckinPhotoSource.CAMERA);
        return photo;
    }
}
