package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.FollowingMomentRefRequest;
import com.ds.goroute.dto.response.FollowingMomentsResponse;
import com.ds.goroute.dto.response.PublicTripResponse;
import com.ds.goroute.dto.response.UserCheckinResponse;
import com.ds.goroute.entity.Notification;
import com.ds.goroute.repository.NotificationRepository;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.TripService;
import com.ds.goroute.service.UserBlockService;
import com.ds.goroute.service.UserCheckinService;
import com.ds.goroute.service.notification.NotificationChangePublisher;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.FollowingPostType;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The moments popup: unread posts of the people the viewer follows (epic 12). */
class FollowingMomentsServiceImplTest {

    private final BusinessConfigService config = mock(BusinessConfigService.class);
    private final BetaAccessService betaAccess = mock(BetaAccessService.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final NotificationChangePublisher changes = mock(NotificationChangePublisher.class);
    private final UserBlockService blocks = mock(UserBlockService.class);
    private final UserCheckinService checkins = mock(UserCheckinService.class);
    private final TripService trips = mock(TripService.class);
    private final FollowingMomentsServiceImpl service = new FollowingMomentsServiceImpl(
            config, betaAccess, notifications, changes, blocks, checkins, trips, new Gson());

    private final UUID viewer = UUID.randomUUID();
    private final UUID author = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(config.getBoolean(BusinessConfigKey.SOCIAL_FOLLOWER_FANOUT_ENABLED)).thenReturn(true);
        when(config.getInt(BusinessConfigKey.SOCIAL_MOMENTS_WINDOW_HOURS)).thenReturn(72);
        when(blocks.blockedAmong(any(), any())).thenReturn(Set.of());
        when(checkins.findVisibleByIds(any(), any())).thenReturn(Map.of());
        when(trips.getPublicTripCards(any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Off for a viewer outside the beta list: nothing is read")
    void offForNonBetaViewer() {
        when(config.getBoolean(BusinessConfigKey.SOCIAL_FOLLOWER_FANOUT_ENABLED)).thenReturn(false);

        FollowingMomentsResponse response = service.unread(viewer);

        assertThat(response.isEnabled()).isFalse();
        assertThat(response.getItems()).isEmpty();
        verify(notifications, never()).findUnreadFollowingPosts(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Returns the unread posts newest first, with the feed card of each")
    void returnsUnreadPostsWithCards() {
        UUID checkinId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        when(notifications.findUnreadFollowingPosts(viewer, 72, 20)).thenReturn(List.of(
                row(FollowingPostType.TRIP, tripId), row(FollowingPostType.CHECKIN, checkinId)));
        when(checkins.findVisibleByIds(viewer, List.of(checkinId))).thenReturn(Map.of(checkinId,
                UserCheckinResponse.builder().id(checkinId).userId(author).build()));
        when(trips.getPublicTripCards(viewer, List.of(tripId))).thenReturn(List.of(
                PublicTripResponse.builder().id(tripId).ownerId(author).build()));

        FollowingMomentsResponse response = service.unread(viewer);

        assertThat(response.isEnabled()).isTrue();
        assertThat(response.getItems()).extracting(item -> item.getPostId()).containsExactly(tripId, checkinId);
        assertThat(response.getItems().get(0).getTrip()).isNotNull();
        assertThat(response.getItems().get(1).getCheckin()).isNotNull();
        verify(notifications, never()).markFollowingPostsRead(any(), any());
    }

    @Test
    @DisplayName("A post that is gone, or by someone in a block, is marked read and left out")
    void gonePostsAreMarkedRead() {
        UUID deleted = UUID.randomUUID();
        UUID blockedPost = UUID.randomUUID();
        UUID blockedAuthor = UUID.randomUUID();
        when(notifications.findUnreadFollowingPosts(viewer, 72, 20)).thenReturn(List.of(
                row(FollowingPostType.CHECKIN, deleted), row(FollowingPostType.CHECKIN, blockedPost)));
        when(checkins.findVisibleByIds(eq(viewer), any())).thenReturn(Map.of(blockedPost,
                UserCheckinResponse.builder().id(blockedPost).userId(blockedAuthor).build()));
        when(blocks.blockedAmong(viewer, Set.of(blockedAuthor))).thenReturn(Set.of(blockedAuthor));
        when(notifications.markFollowingPostsRead(any(), any())).thenReturn(2);

        FollowingMomentsResponse response = service.unread(viewer);

        assertThat(response.getItems()).isEmpty();
        verify(notifications).markFollowingPostsRead(viewer, Set.of(
                "FOLLOWING:CHECKIN:" + deleted, "FOLLOWING:CHECKIN:" + blockedPost));
        verify(changes).listChanged(viewer);
    }

    @Test
    @DisplayName("Marking posts read uses the same key the fan-out wrote")
    void markReadByPost() {
        UUID checkinId = UUID.randomUUID();
        when(notifications.markFollowingPostsRead(any(), any())).thenReturn(1);

        int changed = service.markRead(viewer, List.of(
                new FollowingMomentRefRequest(FollowingPostType.CHECKIN, checkinId)));

        assertThat(changed).isEqualTo(1);
        verify(notifications).markFollowingPostsRead(viewer, Set.of("FOLLOWING:CHECKIN:" + checkinId));
        verify(changes).listChanged(viewer);
    }

    @Test
    @DisplayName("Marking posts already read tells no device")
    void markReadNothingChanged() {
        when(notifications.markFollowingPostsRead(any(), any())).thenReturn(0);

        service.markRead(viewer, List.of(new FollowingMomentRefRequest(FollowingPostType.TRIP, UUID.randomUUID())));

        verify(changes, never()).listChanged(any());
    }

    private Notification row(FollowingPostType postType, UUID postId) {
        return Notification.builder()
                .id(UUID.randomUUID())
                .userId(viewer)
                .type(postType.notificationType())
                .data(new Gson().toJson(Map.of("postType", postType.name(), "postId", postId.toString(),
                        "type", postType.notificationType().name())))
                .createdAt(LocalDateTime.now())
                .build();
    }

}
