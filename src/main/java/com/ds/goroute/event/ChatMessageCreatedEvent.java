package com.ds.goroute.event;

import com.ds.goroute.service.assistant.AssistantAskerLocation;

import java.util.List;
import java.util.UUID;

/**
 * Published by the chat service when a person's message is stored. The assistant listens after
 * commit to decide whether the message asks it something; the chat itself does not wait.
 *
 * @param requestedMentionIds the mentions as sent, before they are narrowed to thread members
 *                            (Pengoo is not a member row, so the narrowed list never names it)
 * @param language            the sender's app language for this request
 * @param senderLocation      where the sender's device was, already rounded; null when the app
 *                            sent none. Only a question in the 1:1 assistant chat keeps it
 */
public record ChatMessageCreatedEvent(
        UUID conversationId,
        UUID messageId,
        UUID senderUserId,
        String messageType,
        String content,
        List<UUID> requestedMentionIds,
        UUID replyToMessageId,
        String language,
        AssistantAskerLocation senderLocation) {

    /** A message sent with no device position. */
    public ChatMessageCreatedEvent(UUID conversationId, UUID messageId, UUID senderUserId, String messageType,
                                   String content, List<UUID> requestedMentionIds, UUID replyToMessageId,
                                   String language) {
        this(conversationId, messageId, senderUserId, messageType, content, requestedMentionIds, replyToMessageId,
                language, null);
    }
}
