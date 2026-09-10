package com.ds.goroute.service;

import com.ds.goroute.dto.response.TripRealtimeEventResponse;
import com.ds.goroute.type.TripRealtimeEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TripRealtimePublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void emitsOnlyAfterOwningTransactionCommits() {
        TripRealtimePublisher publisher = new TripRealtimePublisher(messagingTemplate);
        UUID tripId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishAfterCommit(
                TripRealtimeEventType.ACTIVITY_UPDATED,
                tripId,
                activityId,
                Map.of("source", "itinerary"));

        verify(messagingTemplate, never()).convertAndSend(
                eq("/topic/trips/" + tripId), any(Object.class));

        TransactionSynchronization synchronization = TransactionSynchronizationManager
                .getSynchronizations()
                .getFirst();
        synchronization.afterCommit();

        ArgumentCaptor<TripRealtimeEventResponse> event =
                ArgumentCaptor.forClass(TripRealtimeEventResponse.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/trips/" + tripId), event.capture());
        assertThat(event.getValue().eventId()).isNotNull();
        assertThat(event.getValue().version()).isEqualTo(1);
        assertThat(event.getValue().type()).isEqualTo("activity.updated");
        assertThat(event.getValue().tripId()).isEqualTo(tripId);
        assertThat(event.getValue().entityId()).isEqualTo(activityId);
        assertThat(event.getValue().actorId()).isNull();
        assertThat(event.getValue().payload()).containsEntry("source", "itinerary");
    }

    @Test
    void doesNotEmitWhenOwningTransactionRollsBack() {
        TripRealtimePublisher publisher = new TripRealtimePublisher(messagingTemplate);
        UUID tripId = UUID.randomUUID();

        TransactionSynchronizationManager.initSynchronization();
        publisher.publishAfterCommit(
                TripRealtimeEventType.EXPENSE_UPDATED, tripId, UUID.randomUUID());

        TransactionSynchronization synchronization = TransactionSynchronizationManager
                .getSynchronizations().get(0);
        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }
}
