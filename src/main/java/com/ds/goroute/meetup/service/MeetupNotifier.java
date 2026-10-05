package com.ds.goroute.meetup.service;

import com.ds.goroute.entity.User;
import com.ds.goroute.meetup.domain.Meetup;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.notification.NotificationMessage;
import com.ds.goroute.service.notification.NotificationTemplateRenderer;
import com.ds.goroute.type.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Sends the eight meetup notifications. Data carries {@code meetupId, meetupTitle, hostName,
 * userName, reason} and {@code deepLink = "/meetups/<id>"}; title and body are rendered in each
 * recipient's language.
 *
 * <p>A failed notification never undoes the state change that caused it. A try/catch alone cannot
 * promise that: {@code NotificationService.createNotification} is itself {@code @Transactional}, so
 * a failure inside it marks the caller's transaction rollback-only (and a failed insert aborts the
 * PostgreSQL transaction outright), and the approve/join/cancel would then fail at commit. So when
 * called inside a transaction, the send waits until that transaction has committed and then runs
 * in a transaction of its own; a failure there is logged and dropped. Outside a transaction it is
 * sent at once.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeetupNotifier {

    static final String DEEP_LINK_PREFIX = "/meetups/";

    private final UserRepository users;
    private final NotificationTemplateRenderer templateRenderer;
    private final NotificationService notificationService;
    private final PlatformTransactionManager transactionManager;

    /** One recipient; {@code reasonFor} turns the recipient's language into the reason text. */
    public void send(UUID recipientId, NotificationType type, Meetup meetup, String hostName, String userName,
                     Function<String, String> reasonFor, UUID actorId) {
        if (recipientId == null) {
            return;
        }
        UUID meetupId = meetup.getId();
        String meetupTitle = meetup.getTitle();
        Runnable delivery = () -> deliver(recipientId, type, meetupId, meetupTitle, hostName, userName, reasonFor,
                actorId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            try {
                delivery.run();
            } catch (RuntimeException exception) {
                logFailure(type, meetupId, recipientId, exception);
            }
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                TransactionTemplate own = new TransactionTemplate(transactionManager);
                own.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                try {
                    own.executeWithoutResult(status -> delivery.run());
                } catch (RuntimeException exception) {
                    logFailure(type, meetupId, recipientId, exception);
                }
            }
        });
    }

    /** The same notification to several people, each in their own language. */
    public void sendAll(Collection<UUID> recipientIds, NotificationType type, Meetup meetup, String hostName,
                        Function<String, String> reasonFor, UUID actorId) {
        for (UUID recipientId : recipientIds) {
            send(recipientId, type, meetup, hostName, null, reasonFor, actorId);
        }
    }

    /** Renders and stores one notification. */
    private void deliver(UUID recipientId, NotificationType type, UUID meetupId, String meetupTitle, String hostName,
                         String userName, Function<String, String> reasonFor, UUID actorId) {
        String language = users.findById(recipientId).map(User::getLanguage).orElse(null);
        Map<String, Object> data = new HashMap<>();
        data.put("meetupId", meetupId.toString());
        data.put("meetupTitle", meetupTitle);
        data.put("hostName", hostName == null ? "" : hostName);
        data.put("userName", userName == null ? "" : userName);
        String reason = reasonFor == null ? null : reasonFor.apply(language);
        data.put("reason", reason == null ? "" : reason);
        data.put("deepLink", DEEP_LINK_PREFIX + meetupId);
        NotificationMessage message = templateRenderer.render(type, data, language);
        notificationService.createNotification(recipientId, null, type, message.title(), message.body(),
                data, actorId);
    }

    private static void logFailure(NotificationType type, UUID meetupId, UUID recipientId, RuntimeException exception) {
        log.warn("Could not send {} for meetup {} to {}: {}", type, meetupId, recipientId, exception.getMessage());
    }
}
