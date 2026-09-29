package com.ds.goroute.service.notification;

import com.ds.goroute.repository.NotificationRepository;
import com.ds.goroute.service.UserRealtimePublisher;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.type.UserRealtimeEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tells a person's other devices that their notification list changed ({@code notification.changed}).
 *
 * <p>At most one event per person per transaction, sent after it commits: a trip fan-out or a
 * read-all touches many rows and the app only needs to know to refetch once. The unread count
 * rides along because it is one indexed count, read just before commit so it matches what
 * committed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationChangePublisher {

    static final String NOTIFICATION_ID = "notificationId";
    static final String TYPE = "type";
    static final String UNREAD_COUNT = "unreadCount";

    private final UserRealtimePublisher publisher;
    private final NotificationRepository notifications;

    /** One row of this person's was created or refreshed. */
    public void rowChanged(UUID userId, UUID notificationId, NotificationType type) {
        record(userId, new Change(notificationId, type));
    }

    /** Rows were read or removed; there is no single row to name. */
    public void listChanged(UUID userId) {
        record(userId, new Change(null, null));
    }

    private void record(UUID userId, Change change) {
        if (userId == null) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            Map<UUID, Change> single = new LinkedHashMap<>();
            single.put(userId, change);
            countUnread(single);
            send(single);
            return;
        }
        batch().add(userId, change);
    }

    /**
     * The batch of the current transaction. Found among its synchronizations rather than bound
     * as a resource, because synchronizations are suspended with a REQUIRES_NEW transaction and
     * resumed after it, so an inner transaction gets its own batch.
     */
    private Batch batch() {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            if (synchronization instanceof Batch batch && batch.owner == this) {
                return batch;
            }
        }
        Batch batch = new Batch(this);
        TransactionSynchronizationManager.registerSynchronization(batch);
        return batch;
    }

    private void countUnread(Map<UUID, Change> changes) {
        changes.forEach((userId, change) -> {
            try {
                change.unreadCount = notifications.countUnread(userId);
            } catch (RuntimeException exception) {
                // The count is a convenience; the client refetches it without one.
                log.debug("Could not count unread notifications of {}: {}", userId, exception.getMessage());
            }
        });
    }

    private void send(Map<UUID, Change> changes) {
        changes.forEach((userId, change) -> publisher.send(
                UserRealtimeEventType.NOTIFICATION_CHANGED, userId, change.payload()));
    }

    private static final class Change {
        private final UUID notificationId;
        private final NotificationType type;
        private Integer unreadCount;

        private Change(UUID notificationId, NotificationType type) {
            this.notificationId = notificationId;
            this.type = type;
        }

        /** Two different changes for one person collapse into "something changed". */
        private Change merge(Change other) {
            boolean isSameRow = notificationId != null && notificationId.equals(other.notificationId);
            return isSameRow ? this : new Change(null, null);
        }

        private Map<String, Object> payload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            if (notificationId != null) payload.put(NOTIFICATION_ID, notificationId.toString());
            if (type != null) payload.put(TYPE, type.name());
            if (unreadCount != null) payload.put(UNREAD_COUNT, unreadCount);
            return payload;
        }
    }

    private static final class Batch implements TransactionSynchronization {
        private final NotificationChangePublisher owner;
        private final Map<UUID, Change> changes = new LinkedHashMap<>();

        private Batch(NotificationChangePublisher owner) {
            this.owner = owner;
        }

        private void add(UUID userId, Change change) {
            changes.merge(userId, change, Change::merge);
        }

        @Override
        public void beforeCommit(boolean readOnly) {
            owner.countUnread(changes);
        }

        @Override
        public void afterCommit() {
            owner.send(changes);
        }
    }
}
