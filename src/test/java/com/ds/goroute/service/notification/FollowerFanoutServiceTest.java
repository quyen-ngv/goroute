package com.ds.goroute.service.notification;

import com.ds.goroute.dto.response.CheckinPhotoResponse;
import com.ds.goroute.dto.response.PublicTripResponse;
import com.ds.goroute.dto.response.UserCheckinResponse;
import com.ds.goroute.entity.FollowerFanoutLog;
import com.ds.goroute.entity.User;
import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.repository.FollowerFanoutLogRepository;
import com.ds.goroute.repository.NotificationRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.TripService;
import com.ds.goroute.service.UserCheckinService;
import com.ds.goroute.service.UserRealtimePublisher;
import com.ds.goroute.service.external.FirebaseService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.ContentVisibility;
import com.ds.goroute.type.FollowerFanoutStatus;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.type.UserRealtimeEventType;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Telling followers about a new check-in or public trip (epic 12). */
class FollowerFanoutServiceTest {

    private final BusinessConfigService config = mock(BusinessConfigService.class);
    private final BetaAccessService betaAccess = mock(BetaAccessService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserCheckinRepository checkinRepository = mock(UserCheckinRepository.class);
    private final UserCheckinService checkinService = mock(UserCheckinService.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final TripService tripService = mock(TripService.class);
    private final ContentModerationService moderation = mock(ContentModerationService.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final FollowerFanoutLogRepository logs = mock(FollowerFanoutLogRepository.class);
    private final FirebaseService firebase = mock(FirebaseService.class);
    private final UserRealtimePublisher realtime = mock(UserRealtimePublisher.class);

    private final FollowerFanoutService service = new FollowerFanoutService(config, betaAccess, userRepository,
            checkinRepository, checkinService, tripRepository, tripService, moderation, notifications, logs,
            firebase, realtime, new Gson());

    private final UUID author = UUID.randomUUID();
    private final UUID checkinId = UUID.randomUUID();
    private final UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID third = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @BeforeEach
    void setUp() {
        when(config.getBoolean(BusinessConfigKey.SOCIAL_FOLLOWER_FANOUT_ENABLED)).thenReturn(true);
        when(config.getInt(BusinessConfigKey.SOCIAL_FOLLOWER_FANOUT_BATCH_SIZE)).thenReturn(2);
        when(config.getInt(BusinessConfigKey.SOCIAL_FOLLOWER_FANOUT_PAUSE_MS)).thenReturn(0);
        when(config.getInt(BusinessConfigKey.SOCIAL_FOLLOWER_PUSH_COOLDOWN_MINUTES)).thenReturn(30);
        when(config.getInt(BusinessConfigKey.SOCIAL_FOLLOWER_PUSH_DAILY_MAX)).thenReturn(10);
        when(userRepository.findById(author)).thenReturn(Optional.of(
                User.builder().id(author).fullName("Minh").username("minh").build()));
        when(checkinService.findVisibleByIds(author, List.of(checkinId))).thenReturn(Map.of(checkinId,
                UserCheckinResponse.builder().id(checkinId).userId(author).locationName("Hồ Gươm")
                        .photos(List.of(CheckinPhotoResponse.builder().url("https://cdn/p.jpg").build()))
                        .build()));
        when(checkinRepository.findById(checkinId)).thenReturn(Optional.of(UserCheckin.builder()
                .id(checkinId).userId(author).visibility(ContentVisibility.PUBLIC).isRemoved(false).build()));
        when(moderation.takenDownIds(any(), any())).thenReturn(Set.of());
        when(notifications.insertFollowerNotifications(anyList(), any(), anyString(), any(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(notifications.findOverFollowingDailyCap(anyList(), anyInt(), anyInt())).thenReturn(List.of());
        when(firebase.sendPushToUsers(any(), any(), any())).thenAnswer(invocation ->
                ((Collection<?>) invocation.getArgument(0)).size());
    }

    @Test
    @DisplayName("Walks the followers chunk by chunk and writes one row per follower")
    void walksFollowersInChunks() {
        when(userRepository.findNotifiableFollowerIds(eq(author), isNull(), isNull(), eq(2)))
                .thenReturn(List.of(first, second));
        when(userRepository.findNotifiableFollowerIds(author, second, null, 2)).thenReturn(List.of(third));

        service.fanOutCheckin(checkinId, author);

        verify(notifications).insertFollowerNotifications(eq(List.of(first, second)),
                eq(NotificationType.FOLLOWING_CHECKIN), anyString(), eq(author), eq("FOLLOWING:CHECKIN:" + checkinId));
        verify(notifications).insertFollowerNotifications(eq(List.of(third)),
                eq(NotificationType.FOLLOWING_CHECKIN), anyString(), eq(author), eq("FOLLOWING:CHECKIN:" + checkinId));
        verify(firebase, times(2)).sendPushToUsers(any(), eq(NotificationType.FOLLOWING_CHECKIN), any());
        verify(realtime, times(3)).send(eq(UserRealtimeEventType.NOTIFICATION_CHANGED), any(), any());

        ArgumentCaptor<FollowerFanoutLog> trace = ArgumentCaptor.forClass(FollowerFanoutLog.class);
        verify(logs).insert(trace.capture());
        assertThat(trace.getValue().isPushEnabled()).isTrue();
        assertThat(trace.getValue().getFollowerTotal()).isEqualTo(3);
        assertThat(trace.getValue().getNotifiedCount()).isEqualTo(3);
        assertThat(trace.getValue().getPushedCount()).isEqualTo(3);
        assertThat(trace.getValue().getLastFollowerId()).isEqualTo(third);
        verify(logs).finish(trace.getValue().getId(), FollowerFanoutStatus.DONE, null);
    }

    @Test
    @DisplayName("Row data names the author, the place, the photo and where a tap lands")
    @SuppressWarnings("unchecked")
    void rowDataDescribesThePost() {
        when(userRepository.findNotifiableFollowerIds(eq(author), isNull(), isNull(), eq(2)))
                .thenReturn(List.of(first));

        service.fanOutCheckin(checkinId, author);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(notifications).insertFollowerNotifications(anyList(), any(), json.capture(), any(), anyString());
        Map<String, Object> data = new Gson().fromJson(json.getValue(), Map.class);
        assertThat(data).containsEntry("actorName", "Minh")
                .containsEntry("placeName", "Hồ Gươm")
                .containsEntry("imageUrl", "https://cdn/p.jpg")
                .containsEntry("postType", "CHECKIN")
                .containsEntry("postId", checkinId.toString())
                .containsEntry("deepLink", "/checkins/" + checkinId)
                .doesNotContainKey("postCount");
    }

    @Test
    @DisplayName("Inside the author's cooldown rows are written but nobody is pushed")
    void cooldownWritesRowsWithoutPush() {
        when(logs.isPushCoolingDown(author, 30)).thenReturn(true);
        when(userRepository.findNotifiableFollowerIds(eq(author), isNull(), isNull(), eq(2)))
                .thenReturn(List.of(first));

        service.fanOutCheckin(checkinId, author);

        verify(notifications).insertFollowerNotifications(anyList(), any(), anyString(), any(), anyString());
        verify(firebase, never()).sendPushToUsers(any(), any(), any());
        ArgumentCaptor<FollowerFanoutLog> trace = ArgumentCaptor.forClass(FollowerFanoutLog.class);
        verify(logs).insert(trace.capture());
        assertThat(trace.getValue().isPushEnabled()).isFalse();
    }

    @Test
    @DisplayName("The first push after a cooldown counts the posts it held back")
    @SuppressWarnings("unchecked")
    void pushCountsHeldBackPosts() {
        when(logs.countHeldBackSinceLastPush(author)).thenReturn(2);
        when(userRepository.findNotifiableFollowerIds(eq(author), isNull(), isNull(), eq(2)))
                .thenReturn(List.of(first));

        service.fanOutCheckin(checkinId, author);

        ArgumentCaptor<Map<String, Object>> pushData = ArgumentCaptor.forClass(Map.class);
        verify(firebase).sendPushToUsers(any(), any(), pushData.capture());
        assertThat(pushData.getValue()).containsEntry("postCount", 3);
    }

    @Test
    @DisplayName("Followers past the daily maximum get the row but not the push")
    @SuppressWarnings("unchecked")
    void dailyCapSkipsThePush() {
        when(userRepository.findNotifiableFollowerIds(eq(author), isNull(), isNull(), eq(2)))
                .thenReturn(List.of(first, second));
        when(notifications.findOverFollowingDailyCap(List.of(first, second), 24, 10)).thenReturn(List.of(second));

        service.fanOutCheckin(checkinId, author);

        ArgumentCaptor<Collection<UUID>> pushed = ArgumentCaptor.forClass(Collection.class);
        verify(firebase).sendPushToUsers(pushed.capture(), any(), any());
        assertThat(pushed.getValue()).containsExactly(first);
    }

    @Test
    @DisplayName("While the feature is off only beta followers are walked")
    void offReachesOnlyBetaFollowers() {
        when(config.getBoolean(BusinessConfigKey.SOCIAL_FOLLOWER_FANOUT_ENABLED)).thenReturn(false);
        when(betaAccess.betaUsernames()).thenReturn(Set.of("ann"));
        when(userRepository.findNotifiableFollowerIds(author, null, Set.of("ann"), 2)).thenReturn(List.of(first));

        service.fanOutCheckin(checkinId, author);

        verify(userRepository).findNotifiableFollowerIds(author, null, Set.of("ann"), 2);
        verify(notifications).insertFollowerNotifications(eq(List.of(first)), any(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("Off with an empty beta list does nothing at all, not even a trace row")
    void offWithNoBetaUsersDoesNothing() {
        when(config.getBoolean(BusinessConfigKey.SOCIAL_FOLLOWER_FANOUT_ENABLED)).thenReturn(false);
        when(betaAccess.betaUsernames()).thenReturn(Set.of());

        service.fanOutCheckin(checkinId, author);

        verify(logs, never()).insert(any());
        verify(userRepository, never()).findNotifiableFollowerIds(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("A check-in nobody may see any more is not announced")
    void invisibleCheckinIsSkipped() {
        when(checkinService.findVisibleByIds(author, List.of(checkinId))).thenReturn(Map.of());

        service.fanOutCheckin(checkinId, author);

        verify(logs, never()).insert(any());
    }

    @Test
    @DisplayName("A trip announced before is never announced again")
    void tripIsAnnouncedOnce() {
        UUID tripId = UUID.randomUUID();
        when(tripService.getPublicTripCards(author, List.of(tripId))).thenReturn(List.of(
                PublicTripResponse.builder().id(tripId).ownerId(author).name("Đà Lạt").build()));
        when(tripRepository.claimFollowersNotification(tripId)).thenReturn(false);

        service.fanOutTrip(tripId, author);

        verify(logs, never()).insert(any());
        verify(notifications, never()).insertFollowerNotifications(anyList(), any(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("A newly public trip is announced with its title")
    void newlyPublicTripIsAnnounced() {
        UUID tripId = UUID.randomUUID();
        when(tripService.getPublicTripCards(author, List.of(tripId))).thenReturn(List.of(
                PublicTripResponse.builder().id(tripId).ownerId(author).name("Đà Lạt").build()));
        when(tripRepository.claimFollowersNotification(tripId)).thenReturn(true);
        when(userRepository.findNotifiableFollowerIds(eq(author), isNull(), isNull(), eq(2)))
                .thenReturn(List.of(first));

        service.fanOutTrip(tripId, author);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(notifications).insertFollowerNotifications(eq(List.of(first)), eq(NotificationType.FOLLOWING_TRIP_SHARED),
                json.capture(), eq(author), eq("FOLLOWING:TRIP:" + tripId));
        assertThat(json.getValue()).contains("Đà Lạt").contains("/tour/trip/" + tripId);
    }

    @Test
    @DisplayName("A post taken down mid-walk stops the rest of its followers being told")
    void stopsWhenThePostDisappears() {
        when(userRepository.findNotifiableFollowerIds(eq(author), isNull(), isNull(), eq(2)))
                .thenReturn(List.of(first, second));
        when(moderation.takenDownIds(any(), any())).thenReturn(Set.of(checkinId));

        service.fanOutCheckin(checkinId, author);

        verify(userRepository, never()).findNotifiableFollowerIds(author, second, null, 2);
        ArgumentCaptor<FollowerFanoutLog> trace = ArgumentCaptor.forClass(FollowerFanoutLog.class);
        verify(logs).insert(trace.capture());
        verify(logs).finish(trace.getValue().getId(), FollowerFanoutStatus.CANCELLED, null);
    }

    @Test
    @DisplayName("A failure is recorded on the trace and never escapes")
    void failureIsTraced() {
        when(userRepository.findNotifiableFollowerIds(eq(author), isNull(), isNull(), eq(2)))
                .thenThrow(new IllegalStateException("db down"));

        service.fanOutCheckin(checkinId, author);

        ArgumentCaptor<FollowerFanoutLog> trace = ArgumentCaptor.forClass(FollowerFanoutLog.class);
        verify(logs).insert(trace.capture());
        verify(logs).finish(trace.getValue().getId(), FollowerFanoutStatus.FAILED, "db down");
    }
}
