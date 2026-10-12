package com.ds.goroute.event;

import java.util.List;
import java.util.UUID;

/**
 * Published by the chat service when a person's message is stored. The assistant listens after
 * commit to decide whether the message asks it something; the chat itself does not wait.
 *
 * @param requestedMentionIds the mentions as sent, before they are narrowed to thread members
 *                            (Pengoo is not a member row, so the narrowed list never names it)
 * @param language            the sender's app language for this request
 */
public record ChatMessageCreatedEvent(
        UUID conversationId,
        UUID messageId,
        UUID senderUserId,
        String messageType,
        String content,
        List<UUID> requestedMentionIds,
        UUID replyToMessageId,
        String language) {
}
