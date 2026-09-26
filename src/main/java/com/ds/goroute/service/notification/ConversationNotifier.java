package com.ds.goroute.service.notification;

import com.ds.goroute.dto.response.MarketplaceMessageResponse;
import com.ds.goroute.entity.MarketplaceConversation;
import com.ds.goroute.entity.Notification;
import com.ds.goroute.entity.User;
import com.ds.goroute.repository.MarketplaceChatRepository;
import com.ds.goroute.repository.NotificationRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.type.MarketplaceConversationType;
import com.ds.goroute.type.NotificationType;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tells the rest of a conversation that something was said.
 *
 * <p>The WebSocket topic only reaches whoever has the thread open, which is the minority of
 * the time a message matters: the interesting case is the partner whose phone is in a pocket.
 * So a message also becomes a notification.
 *
 * <p>One per conversation, not one per line. A conversation that is already waiting unread
 * has its existing notification refreshed instead of a new one added, reusing the same
 * thirty-minute window that already groups likes and comments — a chat is the one place where
 * per-event notifications would be unbearable. Being mentioned by name is the exception: it
 * gets its own notification, because that is the one a person is waiting for.
 *
 * <p>Separate from the chat service because sending a message and telling people about it fail
 * for different reasons and must not fail together: nothing here throws.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationNotifier {
    /** Groups the notification by thread; matches the key the coalescing query looks for. */
    private static final String TARGET_TYPE = "CONVERSATION";
    private static final int PREVIEW_LENGTH = 80;
    /** Shown when the message is an image or a file and has nothing to quote. */
    private static final String ATTACHMENT_PREVIEW = "📎";
    private final MarketplaceChatRepository conversations;
    private final NotificationRepository notifications;
    private final NotificationService notificationService;
    private final NotificationTemplateRenderer templateRenderer;
    private final UserRepository users;
    private final Gson gson;

    public void notifyNewMessage(UUID conversationId, UUID senderId, MarketplaceMessageResponse message) {
        notifyNewMessage(conversationId, senderId, message, List.of());
    }

    /**
     * @param mentionedUserIds people named in the message, already filtered to members by
     *                         the caller; they are told by name and are not silenced by mute
     */
    public void notifyNewMessage(UUID conversationId, UUID senderId, MarketplaceMessageResponse message,
                                 List<UUID> mentionedUserIds) {
        try {
            MarketplaceConversation conversation = conversations.find(conversationId, null).orElse(null);
            if (conversation == null) return;
            boolean isPrivate = MarketplaceConversationType.isPrivate(
                    conversation.getConversationType(), conversation.getOrganizationId());
            String title = threadTitle(conversation);
            List<UUID> mentioned = mentionedUserIds == null ? List.of() : mentionedUserIds;

            for (UUID recipient : conversations.findNotifiableMemberIds(conversationId)) {
                if (recipient == null || recipient.equals(senderId) || mentioned.contains(recipient)) {
                    continue;
                }
                notifyOne(recipient, senderId, conversationId, message, isPrivate, title);
            }
            for (UUID recipient : mentioned) {
                if (recipient == null || recipient.equals(senderId)) continue;
                notifyMention(recipient, senderId, conversationId, message, isPrivate, title);
            }
        } catch (RuntimeException exception) {
            // The message is already delivered; failing to announce it must not undo that.
            log.warn("Could not notify participants of conversation {}: {}", conversationId, exception.getMessage());
        }
    }

    private void notifyOne(UUID recipientId, UUID senderId, UUID conversationId,
                           MarketplaceMessageResponse message, boolean isPrivate, String title) {
        Map<String, Object> data = payload(conversationId, message, isPrivate, title);
        try {
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
            // Already waiting unread: show the newest line rather than stacking another row.
            existing.setActorId(senderId);
            existing.setData(gson.toJson(data));
            existing.setBody(null);
            notifications.updateSocialNotification(existing);
        } catch (RuntimeException exception) {
            log.warn("Could not notify {} about a message in conversation {}: {}",
                    recipientId, conversationId, exception.getMessage());
        }
    }

    /** A mention is never folded into an existing row: the point of it is to arrive. */
    private void notifyMention(UUID recipientId, UUID senderId, UUID conversationId,
                               MarketplaceMessageResponse message, boolean isPrivate, String title) {
        try {
            Map<String, Object> data = payload(conversationId, message, isPrivate, title);
            data.put("conversationTitle", title == null ? "" : title);
            NotificationMessage rendered = templateRenderer.render(
                    NotificationType.CHAT_MENTION, data, languageOf(recipientId));
            notificationService.createNotification(recipientId, null, NotificationType.CHAT_MENTION,
                    rendered.title(), rendered.body(), data, senderId);
        } catch (RuntimeException exception) {
            log.warn("Could not notify {} about a mention in conversation {}: {}",
                    recipientId, conversationId, exception.getMessage());
        }
    }

    private Map<String, Object> payload(UUID conversationId, MarketplaceMessageResponse message,
                                        boolean isPrivate, String title) {
        Map<String, Object> data = new HashMap<>();
        data.put("targetType", TARGET_TYPE);
        data.put("targetId", conversationId.toString());
        data.put("conversationId", conversationId.toString());
        data.put("senderName", message.getSenderName() == null ? "" : message.getSenderName());
        data.put("conversationTitle", title == null ? "" : title);
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
