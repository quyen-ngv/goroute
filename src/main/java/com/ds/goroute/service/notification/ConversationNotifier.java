package com.ds.goroute.service.notification;

import com.ds.goroute.dto.response.MarketplaceMessageResponse;
import com.ds.goroute.entity.MarketplaceConversationParticipant;
import com.ds.goroute.entity.Notification;
import com.ds.goroute.entity.User;
import com.ds.goroute.repository.MarketplaceChatRepository;
import com.ds.goroute.repository.NotificationRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.NotificationService;
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
 * per-event notifications would be unbearable.
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
        try {
            for (MarketplaceConversationParticipant participant : conversations.findParticipants(List.of(conversationId))) {
                UUID recipient = participant.getUserId();
                if (recipient == null || recipient.equals(senderId)) {
                    continue;
                }
                notifyOne(recipient, senderId, conversationId, message);
            }
        } catch (RuntimeException exception) {
            // The message is already delivered; failing to announce it must not undo that.
            log.warn("Could not notify participants of conversation {}: {}", conversationId, exception.getMessage());
        }
    }

    private void notifyOne(UUID recipientId, UUID senderId, UUID conversationId, MarketplaceMessageResponse message) {
        Map<String, Object> data = payload(conversationId, message);
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

    private Map<String, Object> payload(UUID conversationId, MarketplaceMessageResponse message) {
        Map<String, Object> data = new HashMap<>();
        data.put("targetType", TARGET_TYPE);
        data.put("targetId", conversationId.toString());
        data.put("conversationId", conversationId.toString());
        data.put("senderName", message.getSenderName() == null ? "" : message.getSenderName());
        data.put("preview", preview(message));
        data.put("deepLink", "/marketplace/conversations/" + conversationId);
        return data;
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
