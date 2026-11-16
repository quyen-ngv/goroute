package com.ds.goroute.service.place;

import com.ds.goroute.repository.PlaceEditSuggestionRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.PlaceService;
import com.ds.goroute.service.StarService;
import com.ds.goroute.service.notification.NotificationTemplateRenderer;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.NotificationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reindex, Star and notification run after commit; the Star is paid once and within the daily cap. */
class PlaceEditNotifierTest {

    private final PlaceService placeService = mock(PlaceService.class);
    private final StarService starService = mock(StarService.class);
    private final BusinessConfigService config = mock(BusinessConfigService.class);
    private final PlaceEditSuggestionRepository suggestions = mock(PlaceEditSuggestionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final PlaceEditNotifier notifier = new PlaceEditNotifier(placeService, starService, config, suggestions,
            users, new NotificationTemplateRenderer(), notifications, transactionManager);

    private final UUID suggestionId = UUID.randomUUID();
    private final UUID placeId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void nothingRunsBeforeTheReviewCommits() {
        TransactionSynchronizationManager.initSynchronization();

        notifier.afterReview(suggestionId, placeId, userId, true, null, "Phở Thìn", List.of(), null);

        verify(placeService, never()).reindexPlace(any());
        List<TransactionSynchronization> registered = TransactionSynchronizationManager.getSynchronizations();
        assertThat(registered).hasSize(1);

        registered.get(0).afterCommit();
        verify(placeService).reindexPlace(placeId);
        verify(placeService).evictSearchCache();
    }

    @Test
    void anApprovalPaysOneStarUnderItsOwnReference() {
        when(config.getInt(BusinessConfigKey.PLACE_EDIT_REWARD_STARS)).thenReturn(1);
        when(config.getInt(BusinessConfigKey.PLACE_EDIT_MAX_REWARDS_PER_DAY)).thenReturn(5);
        when(suggestions.countRewardsSince(eq(userId), any())).thenReturn(2L);

        notifier.reward(userId, suggestionId);

        verify(starService).grant(eq(userId), eq(1), eq("PLACE_EDIT"), eq("place_edit:" + suggestionId), anyString());
    }

    @Test
    void theDailyRewardCapStopsPaying() {
        when(config.getInt(BusinessConfigKey.PLACE_EDIT_REWARD_STARS)).thenReturn(1);
        when(config.getInt(BusinessConfigKey.PLACE_EDIT_MAX_REWARDS_PER_DAY)).thenReturn(5);
        when(suggestions.countRewardsSince(eq(userId), any())).thenReturn(5L);

        notifier.reward(userId, suggestionId);

        verify(starService, never()).grant(any(), anyInt(), any(), any(), any());
    }

    @Test
    void aRejectionIsToldWithThePlaceNameAndNoStarOrReindex() {
        notifier.afterReview(suggestionId, placeId, userId, false, "SPAM", "Phở Thìn", List.of(), null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(notifications).createNotification(eq(userId), eq(null), eq(NotificationType.PLACE_EDIT_REJECTED),
                anyString(), anyString(), data.capture(), eq(null));
        assertThat(data.getValue()).containsEntry("placeName", "Phở Thìn")
                .containsEntry("placeId", placeId.toString())
                .containsEntry("suggestionId", suggestionId.toString())
                .containsEntry("rejectReason", "SPAM")
                .containsEntry("deepLink", "/place/" + placeId);
        verify(starService, never()).grant(any(), anyInt(), any(), any(), any());
        verify(placeService, never()).reindexPlace(any());
    }
}
