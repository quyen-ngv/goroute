package com.ds.goroute.service.notification;

import com.ds.goroute.dto.response.MarketplaceMessageResponse;
import com.ds.goroute.entity.MarketplaceConversation;
import com.ds.goroute.entity.MarketplaceMemberInboxState;
import com.ds.goroute.entity.Notification;
import com.ds.goroute.entity.User;
import com.ds.goroute.repository.MarketplaceChatRepository;
import com.ds.goroute.repository.NotificationRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.type.MarketplaceConversationType;
import com.ds.goroute.type.NotificationType;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Tells the rest of a conversation that something was said.
 *
 * <p>The WebSocket topic only reaches whoever has the thread open, which is the minority of
 * the time a message matters: the interesting case is the partner whose phone is in a pocket.
 * So a message also becomes a notification.
 *
 * <p>One row per conversation, not one per line: a conversation that is already waiting unread
 * has its existing row refreshed instead of a new one added. The push is not coalesced — every
 * message still rings, and the phone stacks them by conversation. Being mentioned by name gets
 * its own row, because that is the one a person is waiting for.
 *
 * <p>Runs after the message commits, off the request thread, one short transaction per
 * recipient. Inside the send's transaction a notification failure marked the whole transaction
 * rollback-only and lost the message despite the catch; and every row written there extended
 * the conversation lock. Nothing here throws.
 */
@Slf4j
@Component
public class ConversationNotifier {
    /** Groups the notification by thread; matches the key the coalescing query looks for. */
    private static final String TARGET_TYPE = "CONVERSATION";
    private static final String SEQUENCE_NO = "sequenceNo";
    private static final int PREVIEW_LENGTH = 80;
    /** Shown when the message is an image or a file and has nothing to quote. */
    private static final String ATTACHMENT_PREVIEW = "📎";
    private final MarketplaceChatRepository conversations;
    private final NotificationRepository notifications;
    private final NotificationService notificationService;
    private final NotificationTemplateRenderer templateRenderer;
    private final UserRepository users;
    private final Gson gson;
    private final TransactionTemplate perRecipient;
    private final Executor executor;

    public ConversationNotifier(MarketplaceChatRepository conversations,
                                NotificationRepository notifications,
                                NotificationService notificationService,
                                NotificationTemplateRenderer templateRenderer,
                                UserRepository users,
                                Gson gson,
                                PlatformTransactionManager transactionManager,
                                @Qualifier("notificationExecutor") Executor executor) {
        this.conversations = conversations;
        this.notifications = notifications;
        this.notificationService = notificationService;
        this.templateRenderer = templateRenderer;
        this.users = users;
        this.gson = gson;
        this.perRecipient = new TransactionTemplate(transactionManager);
        this.perRecipient.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.executor = executor;
    }

    public void notifyNewMessage(UUID conversationId, UUID senderId, MarketplaceMessageResponse message) {
        notifyNewMessage(conversationId, senderId, message, List.of());
    }

    /**
     * Schedules the announcement for after the current transaction commits; nothing is sent
     * for a message that rolls back.
     *
     * @param mentionedUserIds people named in the message, already filtered to members by
     *                         the caller; they are told by name and are not silenced by mute
     */
    public void notifyNewMessage(UUID conversationId, UUID senderId, MarketplaceMessageResponse message,
                                 List<UUID> mentionedUserIds) {
        List<UUID> mentioned = mentionedUserIds == null ? List.of() : List.copyOf(mentionedUserIds);
        Runnable announce = () -> {
            try {
                executor.execute(() -> deliver(conversationId, senderId, message, mentioned));
            } catch (RuntimeException exception) {
                log.warn("Could not queue the announcement of a message in {}: {}",
                        conversationId, exception.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    announce.run();
                }
            });
        } else {
            announce.run();
        }
    }

    /**
     * The reader caught up with a conversation: its chat notifications are read too. Joins the
     * caller's transaction, since it is part of the same "I read this" action.
     */
    public void conversationRead(UUID readerId, UUID conversationId) {
        notificationService.markConversationNotificationsRead(readerId, conversationId);
    }

    void deliver(UUID conversationId, UUID senderId, MarketplaceMessageResponse message, List<UUID> mentioned) {
        try {
            MarketplaceConversation conversation = conversations.find(conversationId, null).orElse(null);
            if (conversation == null) return;
            boolean isPrivate = MarketplaceConversationType.isPrivate(
                    conversation.getConversationType(), conversation.getOrganizationId());
            String title = threadTitle(conversation);
            Map<UUID, Long> readUpTo = readMarkers(conversationId);

            for (UUID recipient : conversations.findNotifiableMemberIds(conversationId)) {
                if (recipient == null || recipient.equals(senderId) || mentioned.contains(recipient)) {
                    continue;
                }
                if (hasAlreadyRead(readUpTo, recipient, message)) continue;
                inOwnTransaction(recipient, conversationId,
                        () -> notifyOne(recipient, senderId, conversationId, message, isPrivate, title));
            }
            for (UUID recipient : mentioned) {
                if (recipient == null || recipient.equals(senderId)) continue;
                inOwnTransaction(recipient, conversationId,
                        () -> notifyMention(recipient, senderId, conversationId, message, isPrivate, title));
            }
        } catch (RuntimeException exception) {
            // The message is already delivered; failing to announce it must not undo that.
            log.warn("Could not notify participants of conversation {}: {}", conversationId, exception.getMessage());
        }
    }

    /** One recipient's failure is theirs alone: logged, and the next recipient is still told. */
    private void inOwnTransaction(UUID recipientId, UUID conversationId, Runnable work) {
        try {
            perRecipient.executeWithoutResult(status -> work.run());
        } catch (RuntimeException exception) {
            log.warn("Could not notify {} about a message in conversation {}: {}",
                    recipientId, conversationId, exception.getMessage());
        }
    }

    /**
     * Somebody with the thread open has usually marked this message read before we get here;
     * a row and a banner for what they are looking at is noise.
     */
    private Map<UUID, Long> readMarkers(UUID conversationId) {
        List<MarketplaceMemberInboxState> states = conversations.findMemberInboxStates(conversationId);
        if (states == null) return Map.of();
        return states.stream()
                .filter(state -> state.getUserId() != null && state.getReadSequenceNo() != null)
                .collect(Collectors.toMap(MarketplaceMemberInboxState::getUserId,
                        MarketplaceMemberInboxState::getReadSequenceNo, Math::max));
    }

    private boolean hasAlreadyRead(Map<UUID, Long> readUpTo, UUID recipient, MarketplaceMessageResponse message) {
        Long read = readUpTo.get(recipient);
        return read != null && message.getSequenceNo() != null && read >= message.getSequenceNo();
    }

    private void notifyOne(UUID recipientId, UUID senderId, UUID conversationId,
                           MarketplaceMessageResponse message, boolean isPrivate, String title) {
        Map<String, Object> data = payload(conversationId, message, isPrivate, title);
        // Two messages announced at once must not both miss the unread row and add two.
        notifications.lockTarget(recipientId, NotificationType.MARKETPLACE_MESSAGE, TARGET_TYPE, conversationId);
        Notification existing = notifications
                .findRecentUnreadSocialNotification(recipientId, NotificationType.MARKETPLACE_MESSAGE,
                        TARGET_TYPE, conversationId)
                .orElse(null);
        if (existing == null) {
            NotificationMessage rendered = templateRenderer.render(
                    NotificationType.MARKETPLACE_MESSAGE, data, languageOf(recipientId));
            notificationService.createNotification(recipientId, null, NotificationType.MARKETPLACE_MESSAGE,
                    rendered.title(), rendered.body(), data, senderId);
            return;
        }
        // Already waiting unread: the row shows the newest line, and this line still rings.
        // Announcements run in parallel, so an older line arriving second keeps the newer text.
        Map<String, Object> stored = readData(existing.getData());
        boolean isOlder = sequenceOf(stored.get(SEQUENCE_NO)) > sequenceOf(message.getSequenceNo());
        if (!isOlder) {
            existing.setActorId(senderId);
        }
        existing.setBody(null);
        notificationService.refreshCoalescedNotification(existing, isOlder ? stored : data, true);
    }

    /** A mention is never folded into an existing row: the point of it is to arrive. */
    private void notifyMention(UUID recipientId, UUID senderId, UUID conversationId,
                               MarketplaceMessageResponse message, boolean isPrivate, String title) {
        Map<String, Object> data = payload(conversationId, message, isPrivate, title);
        NotificationMessage rendered = templateRenderer.render(
                NotificationType.CHAT_MENTION, data, languageOf(recipientId));
        notificationService.createNotification(recipientId, null, NotificationType.CHAT_MENTION,
                rendered.title(), rendered.body(), data, senderId);
    }

    private Map<String, Object> payload(UUID conversationId, MarketplaceMessageResponse message,
                                        boolean isPrivate, String title) {
        Map<String, Object> data = new HashMap<>();
        data.put("targetType", TARGET_TYPE);
        data.put("targetId", conversationId.toString());
        data.put(NotificationDataKeys.CONVERSATION_ID, conversationId.toString());
        data.put("senderName", message.getSenderName() == null ? "" : message.getSenderName());
        data.put("conversationTitle", title == null ? "" : title);
        if (message.getSequenceNo() != null) {
            data.put(SEQUENCE_NO, message.getSequenceNo());
        }
        // What was said travels no further than the two apps that hold the thread. A push
        // payload passes through a third party's servers and sits in a notification log on
        // the device; a private conversation announces that it has something in it and
        // nothing more.
        if (!isPrivate) {
            data.put("preview", preview(message));
        }
        data.put("deepLink", "/chat/" + conversationId);
        return data;
    }

    private Map<String, Object> readData(String rawData) {
        if (rawData == null || rawData.isBlank()) return new HashMap<>();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = gson.fromJson(rawData, Map.class);
            return parsed == null ? new HashMap<>() : new HashMap<>(parsed);
        } catch (RuntimeException malformed) {
            return new HashMap<>();
        }
    }

    private long sequenceOf(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return 0;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException malformed) {
            return 0;
        }
    }

    private String threadTitle(MarketplaceConversation conversation) {
        if (conversation.getTripName() != null) return conversation.getTripName();
        if (conversation.getOrganizationName() != null) return conversation.getOrganizationName();
        if (conversation.getBookingCode() != null) return conversation.getBookingCode();
        return conversation.getOrderCode();
    }

    private String languageOf(UUID userId) {
        return users.findById(userId).map(User::getLanguage).orElse(null);
    }

    /**
     * One line of what was said. Collapsed and cut because it is read in a banner, and a
     * message with no text still has to announce that something arrived.
     */
    private String preview(MarketplaceMessageResponse message) {
        String content = message.getContent();
        if (content == null || content.isBlank()) {
            return message.getAttachments() == null || message.getAttachments().isEmpty() ? "" : ATTACHMENT_PREVIEW;
        }
        String collapsed = content.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= PREVIEW_LENGTH ? collapsed : collapsed.substring(0, PREVIEW_LENGTH - 1) + "…";
    }
}
