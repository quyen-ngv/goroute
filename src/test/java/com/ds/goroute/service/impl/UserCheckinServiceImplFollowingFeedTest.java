package com.ds.goroute.service.impl;

import com.ds.goroute.dto.response.UserCheckinResponse;
import com.ds.goroute.entity.User;
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
import com.ds.goroute.type.ContentVisibility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCheckinServiceImplFollowingFeedTest {

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
    void pageAndSizeAreBoundedBeforeTheyReachTheQuery() {
        UUID viewerId = UUID.randomUUID();

        assertThat(service.followingFeed(viewerId, -3, 500)).isEmpty();
        service.followingFeed(viewerId, 2, 0);
        service.followingFeed(viewerId, 3, 20);

        verify(checkins).findFollowingFeed(viewerId, 50, 0);
        verify(checkins).findFollowingFeed(viewerId, 1, 2);
        verify(checkins).findFollowingFeed(viewerId, 20, 60);
    }

    @Test
    void rowsAreEnrichedForTheViewerWhoAsked() {
        UUID viewerId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID checkinId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 3, 18, 30);
        UserCheckin row = UserCheckin.builder()
                .id(checkinId)
                .userId(authorId)
                .visibility(ContentVisibility.PUBLIC)
                .createdAt(createdAt)
                .build();
        when(checkins.findFollowingFeed(viewerId, 20, 0)).thenReturn(List.of(row));
        when(users.findById(authorId)).thenReturn(Optional.of(
                User.builder().id(authorId).fullName("Minh").avatarUrl("https://cdn/minh.jpg").build()));
        when(checkins.findLikedCheckinIds(viewerId, List.of(checkinId))).thenReturn(List.of(checkinId));

        List<UserCheckinResponse> feed = service.followingFeed(viewerId, 0, 20);

        assertThat(feed).singleElement().satisfies(post -> {
            assertThat(post.getId()).isEqualTo(checkinId);
            assertThat(post.getUserId()).isEqualTo(authorId);
            assertThat(post.getUserDisplayName()).isEqualTo("Minh");
            assertThat(post.getUserAvatarUrl()).isEqualTo("https://cdn/minh.jpg");
            assertThat(post.isHasLiked()).as("liked state belongs to the viewer").isTrue();
            assertThat(post.getCreatedAt()).isEqualTo(createdAt);
        });
    }
}
