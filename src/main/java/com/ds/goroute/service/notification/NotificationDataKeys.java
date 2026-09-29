package com.ds.goroute.service.notification;

import java.util.Set;

/**
 * Names of the notification data fields the server itself reads, as opposed to the ones only
 * the templates and the app read.
 */
public final class NotificationDataKeys {

    /** The notification row a push stands for, so tapping it can mark exactly that row read. */
    public static final String NOTIFICATION_ID = "notificationId";

    /** Present on chat notifications; also the collapse key and thread of a chat push. */
    public static final String CONVERSATION_ID = "conversationId";

    /** When the thing a reminder is about begins (ISO-8601 with offset); bounds the push's lifetime. */
    public static final String STARTS_AT = "startsAt";

    public static final String RECIPIENT_IDS = "recipientIds";
    public static final String EXCLUDED_RECIPIENT_IDS = "excludedRecipientIds";

    /**
     * Routing instructions for choosing recipients. They say who else was told about an event,
     * which is nobody's business on a lock screen, so they never leave the server.
     */
    public static final Set<String> ROUTING_ONLY = Set.of(RECIPIENT_IDS, EXCLUDED_RECIPIENT_IDS);

    private NotificationDataKeys() {
    }
}
